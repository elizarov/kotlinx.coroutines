package kotlinx.coroutines.experimental.internal

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

private typealias Node = LockFreeLinkedListNode

/**
 * Doubly-linked concurrent list node with remove support.
 * Based on paper
 * ["Lock-Free and Practical Doubly Linked List-Based Deques Using Single-Word Compare-and-Swap"](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.140.4693&rep=rep1&type=pdf)
 * by Sundell and Tsigas.
 * The instance of this class serves both as list head/tail sentinel and as the list item.
 * Sentinel node should be never removed.
 */
@Suppress("LeakingThis")
internal open class LockFreeLinkedListNode {
    @Volatile
    private var _next: Any = this // DoubleLinkedNode | Removed | AtomicOperationDescriptor
    @Volatile
    private var prev: Any = this // DoubleLinkedNode | Removed
    @Volatile
    private var removedRef: Removed? = null // lazily cached removed ref to this

    private companion object {
        @JvmStatic
        val NEXT: AtomicReferenceFieldUpdater<Node, Any> =
                AtomicReferenceFieldUpdater.newUpdater(Node::class.java, Any::class.java, "_next")
        @JvmStatic
        val PREV: AtomicReferenceFieldUpdater<Node, Any> =
                AtomicReferenceFieldUpdater.newUpdater(Node::class.java, Any::class.java, "prev")
        @JvmStatic
        val REMOVED_REF: AtomicReferenceFieldUpdater<Node, Removed?> =
            AtomicReferenceFieldUpdater.newUpdater(Node::class.java, Removed::class.java, "removedRef")
    }

    private open class Removed(val ref: Node) {
        override fun toString(): String = "Removed[$ref]"
        open val attachment: Any? get() = null
    }

    private class RemovedWithAttachment(ref: Node, override val attachment: Any?) : Removed(ref) {
        override fun toString(): String = "RemovedWithAttachment[$ref, $attachment]"
    }

    private fun removed(): Removed =
        removedRef ?: Removed(this).also { REMOVED_REF.lazySet(this, it) }

    abstract class ConditionalAdd : AtomicOperationDescriptor() {
        internal lateinit var newNode: Node
        internal lateinit var oldNext: Node

        override fun finish(node: Any?, success: Boolean) {
            node as Node // type assertion
            if (NEXT.compareAndSet(node, this, if (success) newNode else oldNext)) {
                // only the thread the makes this update actually finishes add operation
                if (success) newNode.finishAdd(oldNext)
            }
        }
    }

    val isRemoved: Boolean get() = _next is Removed

    private val isFresh: Boolean get() = _next === this && prev === this

    private val next: Any get() {
        while (true) { // helper loop on _next
            val next = this._next
            if (next !is AtomicOperationDescriptor) return next
            next.perform(this)
        }
    }

    fun next(): Node = next.unwrap()

    // ------ addFirstXXX ------

    private fun addFirstCC(node: Node, conditionalAdd: ConditionalAdd?): Boolean {
        require(node.isFresh)
        conditionalAdd?.newNode = node
        while (true) { // lock-free loop on next
            val next = this.next as Node // this sentinel node is never removed
            PREV.lazySet(node, this)
            NEXT.lazySet(node, next)
            conditionalAdd?.oldNext = next
            if (NEXT.compareAndSet(this, next, conditionalAdd ?: node)) {
                // added successfully (linearized add) -- fixup the list
                return conditionalAdd?.perform(this) ?: run { node.finishAdd(next); true }
            }
        }
    }

    /**
     * Adds first item to this list.
     */
    fun addFirst(node: Node) { addFirstCC(node, null) }

    /**
     * Adds first item to this list atomically if the [condition] is true.
     */
    inline fun addFirstIf(node: Node, crossinline condition: () -> Boolean): Boolean =
        addFirstCC(node, object : ConditionalAdd() {
            override fun prepare(): Boolean = condition()
        })

    fun addFirstIfEmpty(node: Node): Boolean {
        require(node.isFresh)
        PREV.lazySet(node, this)
        NEXT.lazySet(node, this)
        if (!NEXT.compareAndSet(this, this, node)) return false // this is not an empty list!
        // added successfully (linearized add) -- fixup the list
        node.finishAdd(this)
        return true
    }

    fun describeAddFist(node: Node): AtomicOperationPart {
        require(node.isFresh)
        return object : AbstractAtomicOperationPart() {
            override fun onPrepare(next: Node): Boolean {
                PREV.lazySet(node, this)
                NEXT.lazySet(node, next)
                return true
            }
            override fun updatedNext(next: Node): Any = node
            override fun finishOnSuccess(next: Node) = node.finishAdd(next)
        }
    }

    // ------ addLastXXX ------

    private fun addLastCC(node: Node, conditionalAdd: ConditionalAdd?): Boolean {
        require(node.isFresh)
        conditionalAdd?.newNode = node
        while (true) { // lock-free loop on prev.next
            val prev = prevHelper() ?: continue
            PREV.lazySet(node, prev)
            NEXT.lazySet(node, this)
            conditionalAdd?.oldNext = this
            if (NEXT.compareAndSet(prev, this, conditionalAdd ?: node)) {
                // added successfully (linearized add) -- fixup the list
                return conditionalAdd?.perform(prev) ?: run { node.finishAdd(this); true }
            }
        }
    }

    /**
     * Adds last item to this list.
     */
    fun addLast(node: Node) { addLastCC(node, null) }

    /**
     * Adds last item to this list atomically if the [condition] is true.
     */
    inline fun addLastIf(node: Node, crossinline condition: () -> Boolean): Boolean =
        addLastCC(node, object : ConditionalAdd() {
            override fun prepare(): Boolean = condition()
        })

    inline fun addLastIfPrev(node: Node, predicate: (Node) -> Boolean): Boolean {
        require(node.isFresh)
        while (true) { // lock-free loop on prev.next
            val prev = prevHelper() ?: continue
            if (!predicate(prev)) return false
            if (addAfterPrev(node, prev)) return true
        }
    }

    private fun prevHelper(): Node? {
        val prev = this.prev as Node // this sentinel node is never removed
        if (prev.next === this) return prev
        helpInsert(prev)
        return null
    }

    private fun addAfterPrev(node: Node, prev: Node): Boolean {
        PREV.lazySet(node, prev)
        NEXT.lazySet(node, this)
        if (NEXT.compareAndSet(prev, this, node)) {
            // added successfully (linearized add) -- fixup the list
            node.finishAdd(this)
            return true
        }
        return false
    }

    // ------ removeXXX ------

    /**
     * Removes this node from the list. Returns `true` when removed successfully.
     */
    open fun remove(): Boolean {
        while (true) { // lock-free loop on next
            val next = this.next
            if (next is Removed) return false // was already removed -- don't try to help (original thread will take care)
            if (NEXT.compareAndSet(this, next, (next as Node).removed())) {
                // was removed successfully (linearized remove) -- fixup the list
                finishRemove(next)
                return true
            }
        }
    }

    fun removeNextOrNull(): Node? {
        while (true) { // try to linearize
            val next = next()
            if (next === this) return null
            if (next.remove()) return next
        }
    }

    inline fun <reified T : Node> removeNextIfIsInstanceOf(): T? {
        while (true) { // try to linearize
            val next = next()
            if (next === this) return null
            if (next !is T) return null
            if (next.remove()) return next
        }
    }

    fun describeRemove() : AtomicOperationPart? {
        if (isRemoved) return null // fast path if was already removed
        return object : AbstractAtomicOperationPart() {
            override fun updatedNext(next: Node) = next.removed()
            override fun finishOnSuccess(next: Node) = finishRemove(next)
        }
    }

    fun describeRemoveNext() : AtomicOperationPart? {
        val next = next()
        if (next === this) return null
        return next.describeRemove()
    }

    fun removeWithAttachment(attachment: Any?): Boolean {
        while (true) { // lock-free loop on next
            val next = this.next
            if (next is Removed) return false // was already removed -- don't try to help (original thread will take care)
            if (NEXT.compareAndSet(this, next, RemovedWithAttachment(next as Node, attachment))) {
                // was removed successfully (linearized remove) -- fixup the list
                finishRemove(next)
                return true
            }
        }
    }

    fun removedAttachment(): Any? = (_next as? RemovedWithAttachment)?.attachment

    // ------ multi-word atomic operations helper ------

    private abstract inner class AbstractAtomicOperationPart : AtomicOperationPart() {
        var originalNext: Node? = null

        open fun onPrepare(next: Node): Boolean = true
        abstract fun updatedNext(next: Node): Any
        abstract fun finishOnSuccess(next: Node)

        override fun prepare(operation: AtomicOperationDescriptor): Boolean {
            while (true) { // lock free loop on next
                val next = _next // volatile read
                if (_next === operation) return true // already in process of operation -- all is good
                if (next is Removed) return false // cannot update removed (which is a final state) -- failed
                if (next is AtomicOperationDescriptor) {
                    // somebody else's operation is in process -- help it
                    next.perform(this@LockFreeLinkedListNode)
                    continue // and retry
                }
                originalNext = next as Node // must be a node
                if (!onPrepare(next)) return false // some other invariant failed
                if (NEXT.compareAndSet(this@LockFreeLinkedListNode, next, operation)) return true // prepared!!!
            }
        }

        override fun finish(operation: AtomicOperationDescriptor, success: Boolean) {
            val update = if (success) updatedNext(originalNext!!) else originalNext
            if (NEXT.compareAndSet(this@LockFreeLinkedListNode, operation, update)) {
                if (success) finishOnSuccess(originalNext!!)
            }
        }
    }

    // ------ other helpers ------

    private fun finishAdd(next: Node) {
        while (true) {
            val nextPrev = next.prev
            if (nextPrev is Removed || this.next !== next) return // next was removed, remover fixes up links
            if (PREV.compareAndSet(next, nextPrev, this)) {
                if (this.next is Removed) {
                    // already removed
                    next.helpInsert(nextPrev as Node)
                }
                return
            }
        }
    }

    private fun finishRemove(next: Node) {
        helpDelete()
        next.helpInsert(prev.unwrap())
    }

    private fun markPrev(): Node {
        while (true) { // lock-free loop on prev
            val prev = this.prev
            if (prev is Removed) return prev.ref
            if (PREV.compareAndSet(this, prev, (prev as Node).removed())) return prev
        }
    }

    // fixes next links to the left of this node
    private fun helpDelete() {
        var last: Node? = null // will set to the node left of prev when found
        var prev: Node = markPrev()
        var next: Node = (this._next as Removed).ref
        while (true) {
            // move to the right until first non-removed node
            val nextNext = next.next
            if (nextNext is Removed) {
                next.markPrev()
                next = nextNext.ref
                continue
            }
            // move the the left until first non-removed node
            val prevNext = prev.next
            if (prevNext is Removed) {
                if (last != null) {
                    prev.markPrev()
                    NEXT.compareAndSet(last, prev, prevNext.ref)
                    prev = last
                    last = null
                } else {
                    prev = prev.prev.unwrap()
                }
                continue
            }
            if (prevNext !== this) {
                // skipped over some removed nodes to the left -- setup to fixup the next links
                last = prev
                prev = prevNext as Node
                if (prev === next) return // already done!!!
                continue
            }
            // Now prev & next are Ok
            if (NEXT.compareAndSet(prev, this, next)) return // success!
        }
    }

    // fixes prev links from this node
    private fun helpInsert(_prev: Node) {
        var prev: Node = _prev
        var last: Node? = null // will be set so that last.next === prev
        while (true) {
            // move the the left until first non-removed node
            val prevNext = prev.next
            if (prevNext is Removed) {
                if (last !== null) {
                    prev.markPrev()
                    NEXT.compareAndSet(last, prev, prevNext.ref)
                    prev = last
                    last = null
                } else {
                    prev = prev.prev.unwrap()
                }
                continue
            }
            val oldPrev = this.prev
            if (oldPrev is Removed) return // this node was removed, too -- its remover will take care
            if (prevNext !== this) {
                // need to fixup next
                last = prev
                prev = prevNext as Node
                continue
            }
            if (oldPrev === prev) return // it is already linked as needed
            if (PREV.compareAndSet(this, oldPrev, prev)) {
                if (prev.prev !is Removed) return // finish only if prev was not concurrently removed
            }
        }
    }

    private fun Any.unwrap(): Node = if (this is Removed) ref else this as Node

    fun validateNode(prev: Node, next: Node) {
        check(prev === this.prev)
        check(next === this.next)
    }
}

internal open class LockFreeLinkedListHead : LockFreeLinkedListNode() {
    val isEmpty: Boolean get() = next() == this

    /**
     * Iterates over all elements in this list of a specified type.
     */
    inline fun <reified T : Node> forEach(block: (T) -> Unit) {
        var cur: Node = next()
        while (cur != this) {
            if (cur is T) block(cur)
            cur = cur.next()
        }
    }

    // just a defensive programming -- makes sure that list head sentinel is never removed
    final override fun remove() = throw UnsupportedOperationException()

    fun validate() {
        var prev: Node = this
        var cur: Node = next()
        while (cur != this) {
            val next = cur.next()
            cur.validateNode(prev, next)
            prev = cur
            cur = next
        }
        validateNode(prev, next())
    }
}
