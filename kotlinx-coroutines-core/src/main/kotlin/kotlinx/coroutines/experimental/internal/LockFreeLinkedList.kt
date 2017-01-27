package kotlinx.coroutines.experimental.internal

import java.util.concurrent.atomic.AtomicReference
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
    private var _next: Any = this // DoubleLinkedNode | Removed | OpDescriptor
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

    abstract class CondAddOp : AtomicOpDescriptor() {
        internal lateinit var newNode: Node
        internal lateinit var oldNext: Node

        override fun unwrapRef(affected: Any): Any = oldNext

        override fun finish(affected: Any?, success: Boolean) {
            affected as Node // type assertion
            if (NEXT.compareAndSet(affected, this, if (success) newNode else oldNext)) {
                // only the thread the makes this update actually finishes add operation
                if (success) newNode.finishAdd(oldNext)
            }
        }
    }

    val isRemoved: Boolean get() = _next is Removed

    private val isFresh: Boolean get() = _next === this && prev === this

    private val next: Any get() { // Node | Removed
        val result = _next.let { (it as? OpDescriptor)?.unwrapRef(this) ?: it }
        check(result is Node || result is Removed)
        return result
    }

    // use this for operations that will update next
    private val nextForUpdate: Any get() {
        while (true) { // operation helper loop on _next
            val next = this._next
            if (next !is OpDescriptor) return next
            next.perform(this)
        }
    }

    fun next(): Node = next.unwrap()

    // ------ addFirstXXX ------

    private fun addFirstCC(node: Node, condAddOp: CondAddOp?): Boolean {
        require(node.isFresh)
        condAddOp?.newNode = node
        while (true) { // lock-free loop on next
            val next = nextForUpdate as Node // this sentinel node is never removed
            PREV.lazySet(node, this)
            NEXT.lazySet(node, next)
            condAddOp?.oldNext = next
            if (NEXT.compareAndSet(this, next, condAddOp ?: node)) {
                // added successfully (linearized add) -- fixup the list
                return condAddOp?.perform(this) ?: run { node.finishAdd(next); true }
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
        addFirstCC(node, object : CondAddOp() {
            override fun prepare(): Boolean = condition()
        })

    fun addFirstIfEmpty(node: Node): Boolean {
        require(node.isFresh)
        PREV.lazySet(node, this)
        NEXT.lazySet(node, this)
        while (true) {
            val next = nextForUpdate
            if (next !== this) return false // this is not an empty list!
            if (NEXT.compareAndSet(this, this, node)) {
                // added successfully (linearized add) -- fixup the list
                node.finishAdd(this)
                return true
            }
        }
    }

    fun describeAddFirst(node: Node): AtomicOpPart {
        require(node.isFresh)
        return object : AbstractAtomicOpPart() {
            override val affectedNode: Node? get() = this@LockFreeLinkedListNode
            override var originalNext: Node? = null
            override fun onPrepare(affected: Node, next: Node) {
                check(affected == this@LockFreeLinkedListNode)
                originalNext = next // benign race
                // make sure only fresh node is initialized (beware of stale onPrepare invocation)
                PREV.compareAndSet(node, node, this@LockFreeLinkedListNode)
                NEXT.compareAndSet(node, node, next)
            }
            override fun updatedNext(next: Node): Any = node
            override fun finishOnSuccess(affected: Node, next: Node) = node.finishAdd(next)
        }
    }

    // Note: this is mostly for testing (not really optimized yet)
    fun describeAddFirst(supplier: () -> Node): AtomicOpPart {
        return object : AbstractAtomicOpPart() {
            var node = AtomicReference<Node?>()
            override var originalNext: Node? = null
            override val affectedNode: Node? get() = this@LockFreeLinkedListNode
            override fun onPrepare(affected: Node, next: Node) {
                check(affected === this@LockFreeLinkedListNode)
                originalNext = next  // benign race
                // make sure only fresh node is initialized (beware of stale onPrepare invocation)
                node.compareAndSet(null, supplier()) // atomic node creation
                val node = node.get()!!
                PREV.compareAndSet(node, node, this@LockFreeLinkedListNode)
                NEXT.compareAndSet(node, node, next)
            }
            override fun updatedNext(next: Node): Any = node.get()!!
            override fun finishOnSuccess(affected: Node, next: Node) = node.get()!!.finishAdd(next)
        }
    }

    // ------ addLastXXX ------

    private fun addLastCC(node: Node, condAddOp: CondAddOp?): Boolean {
        require(node.isFresh)
        condAddOp?.newNode = node
        while (true) { // lock-free loop on prev.next
            val prev = prevForUpdate()
            PREV.lazySet(node, prev)
            NEXT.lazySet(node, this)
            condAddOp?.oldNext = this
            if (NEXT.compareAndSet(prev, this, condAddOp ?: node)) {
                // added successfully (linearized add) -- fixup the list
                return condAddOp?.perform(prev) ?: run { node.finishAdd(this); true }
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
        addLastCC(node, object : CondAddOp() {
            override fun prepare(): Boolean = condition()
        })

    inline fun addLastIfPrev(node: Node, predicate: (Node) -> Boolean): Boolean {
        require(node.isFresh)
        while (true) { // lock-free loop on prev.next
            val prev = prevForUpdate()
            if (!predicate(prev)) return false
            if (addAfterPrev(node, prev)) return true
        }
    }

    fun describeAddLast(node: Node): AtomicOpPart {
        require(node.isFresh)
        return object : AbstractAtomicOpPart() {
            override fun takeAffectedNode(): Node = prevForUpdate()
            override var affectedNode: Node? = null
            override val originalNext: Node? get() = this@LockFreeLinkedListNode
            override fun retry(next: Any) = next !== this@LockFreeLinkedListNode
            override fun onPrepare(affected: Node, next: Node) {
                check(next === this@LockFreeLinkedListNode)
                affectedNode = affected // benign race
                // make sure only fresh node is initialized (beware of stale onPrepare invocation)
                PREV.compareAndSet(node, node, affected)
                NEXT.compareAndSet(node, node, this@LockFreeLinkedListNode)
            }
            override fun updatedNext(next: Node): Any = node
            override fun finishOnSuccess(affected: Node, next: Node) = node.finishAdd(this@LockFreeLinkedListNode)
        }
    }

    // Note: this is mostly for testing (not really optimized yet)
    fun describeAddLast(supplier: () -> Node): AtomicOpPart {
        return object : AbstractAtomicOpPart() {
            var node = AtomicReference<Node?>()
            override fun takeAffectedNode(): Node = prevForUpdate()

            val an = AtomicReference<Node?>()
            override val affectedNode: Node? get() = an.get()

            override val originalNext: Node? get() = this@LockFreeLinkedListNode
            override fun retry(next: Any) = next !== this@LockFreeLinkedListNode
            override fun onPrepare(affected: Node, next: Node) {
                check(next === this@LockFreeLinkedListNode)
                if (!an.compareAndSet(null, affected)) {
                    if (an.get() != affected)
                        println("!!!")
                }
                // make sure only fresh node is initialized (beware of stale onPrepare invocation)
                node.compareAndSet(null, supplier()) // atomic node creation
                val node = node.get()!!
                PREV.compareAndSet(node, node, affected)
                NEXT.compareAndSet(node, node, this@LockFreeLinkedListNode)
            }
            override fun updatedNext(next: Node): Any = node.get()!!
            override fun finishOnSuccess(affected: Node, next: Node) = node.get()!!.finishAdd(this@LockFreeLinkedListNode)
        }
    }

    private fun prevForUpdate(): Node {
        while (true) {
            val prev = this.prev as Node // this sentinel node is never removed
            if (prev.nextForUpdate === this) return prev
            helpInsert(prev)
        }
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
            val next = nextForUpdate
            if (next is Removed) return false // was already removed -- don't try to help (original thread will take care)
            if (NEXT.compareAndSet(this, next, (next as Node).removed())) {
                // was removed successfully (linearized remove) -- fixup the list
                finishRemove(next)
                return true
            }
        }
    }

    fun removeFirstOrNull(): Node? {
        while (true) { // try to linearize
            val next = next()
            if (next === this) return null
            if (next.remove()) return next
        }
    }

    inline fun <reified T : Node> removeFirstIfIsInstanceOf(): T? {
        while (true) { // try to linearize
            val next = next()
            if (next === this) return null
            if (next !is T) return null
            if (next.remove()) return next
        }
    }

    fun describeRemove() : AtomicOpPart? {
        if (isRemoved) return null // fast path if was already removed
        return object : AbstractAtomicOpPart() {
            override val affectedNode: Node? get() = this@LockFreeLinkedListNode
            override var originalNext: Node? = null
            override fun failed(affected: Node, next: Any): Boolean = next is Removed
            override fun onPrepare(affected: Node, next: Node) { originalNext = next }
            override fun updatedNext(next: Node) = next.removed()
            override fun finishOnSuccess(affected: Node, next: Node) = finishRemove(next)
        }
    }

    fun describeRemoveFirst() : AbstractAtomicOpPart? {
        return object : AbstractAtomicOpPart() {
            override fun takeAffectedNode(): Node = next()
            override var affectedNode: Node? = null
            override var originalNext: Node? = null
            override fun failed(affected: Node, next: Any): Boolean = affected === this@LockFreeLinkedListNode
            override fun retry(next: Any): Boolean = next is Removed
            override fun onPrepare(affected: Node, next: Node) {
                affectedNode = affected
                originalNext = next
            }
            override fun updatedNext(next: Node) = next.removed()
            override fun finishOnSuccess(affected: Node, next: Node) = affected.finishRemove(next)
        }
    }

    fun removeWithAttachment(attachment: Any?): Boolean {
        while (true) { // lock-free loop on next
            val next = nextForUpdate
            if (next is Removed) return false // was already removed -- don't try to help (original thread will take care)
            if (NEXT.compareAndSet(this, next, RemovedWithAttachment(next as Node, attachment))) {
                // was removed successfully (linearized remove) -- fixup the list
                finishRemove(next)
                return true
            }
        }
    }

    fun removedAttachment(): Any? = (_next as? RemovedWithAttachment)?.attachment

    // ------ multi-word atomic operations helpers ------

    internal abstract class AbstractAtomicOpPart : AtomicOpPart() {
        abstract val affectedNode: Node?
        abstract val originalNext: Node?
        open fun takeAffectedNode(): Node = affectedNode!!
        open fun failed(affected: Node, next: Any): Boolean = false // next: Node | Removed
        open fun retry(next: Any): Boolean = false // next: Node | Removed
        abstract fun onPrepare(affected: Node, next: Node)
        abstract fun updatedNext(next: Node): Any
        abstract fun finishOnSuccess(affected: Node, next: Node)

        // This is Harris's RDCSS (Restricted Double-Compare Single Swap) operation
        // It inserts "op" descriptor of when "op" status is still undecided (rolls back otherwise)
        private class PrepareOp(
                val next: Node,
                val op: AtomicOpDescriptor,
                val part: AbstractAtomicOpPart
        ) : OpDescriptor() {
            override fun unwrapRef(affected: Any): Any = next
            override fun perform(affected: Any?): Boolean {
                affected as Node // type assertion
                part.onPrepare(affected, next)
                check(part.affectedNode === affected)
                check(part.originalNext === next)
                val update: Any = if (op.consensus == AtomicOpDescriptor.UNDECIDED) op else next
                NEXT.compareAndSet(affected, this, update)
                return true // go to next part
            }
        }

        override fun unwrapRef(op: AtomicOpDescriptor, affected: Any): Any? =
            if (affected === affectedNode) {
                if (op.consensus == AtomicOpDescriptor.SUCCESS) updatedNext(originalNext!!) else originalNext
            } else null

        override fun prepare(op: AtomicOpDescriptor): Boolean {
            while (true) { // lock free loop on next
                val affected = takeAffectedNode()
                // read its original next pointer first
                val next = affected._next
                // then see if already reached consensus on overall operation
                if (op.consensus != AtomicOpDescriptor.UNDECIDED) return true // already decided -- go to next part
                if (next === op) return true // already in process of operation -- all is good
                if (next is OpDescriptor) {
                    // some other operation is in process -- help it
                    next.perform(affected)
                    continue // and retry
                }
                // next: Node | Removed
                if (failed(affected, next)) return false //  signal failure
                if (retry(next)) continue // retry operation
                val prepareOp = PrepareOp(next as Node, op, this)
                if (NEXT.compareAndSet(affected, next, prepareOp)) {
                    // prepared -- complete preparations
                    return prepareOp.perform(affected)
                }
            }
        }

        override fun finish(op: AtomicOpDescriptor, success: Boolean) {
            val update = if (success) updatedNext(originalNext!!) else originalNext
            val affectedNode = affectedNode
            if (affectedNode == null) {
                check(!success)
                return
            }
            if (NEXT.compareAndSet(affectedNode, op, update)) {
                if (success) finishOnSuccess(affectedNode, originalNext!!)
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
            val prevNext = prev.nextForUpdate // todo: ???
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
            val prevNext = prev.nextForUpdate // todo: ???
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
