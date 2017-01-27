package kotlinx.coroutines.experimental.internal

import org.junit.Assert.*
import org.junit.Test

class LockFreeLinkedListTest {
    private data class IntNode(val i: Int) : LockFreeLinkedListNode()

    @Test
    fun testSimpleAddFirst() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        val n1 = IntNode(1).apply { list.addFirst(this) }
        assertContents(list, 1)
        val n2 = IntNode(2).apply { list.addFirst(this) }
        assertContents(list, 2, 1)
        val n3 = IntNode(3).apply { list.addFirst(this) }
        assertContents(list, 3, 2, 1)
        val n4 = IntNode(4).apply { list.addFirst(this) }
        assertContents(list, 4, 3, 2, 1)
        assertTrue(n1.remove())
        assertContents(list, 4, 3, 2)
        assertTrue(n3.remove())
        assertFalse(n3.remove())
        assertContents(list, 4, 2)
        assertTrue(n4.remove())
        assertContents(list, 2)
        assertTrue(n2.remove())
        assertContents(list)
    }

    @Test
    fun testSimpleAddLast() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        val n1 = IntNode(1).apply { list.addLast(this) }
        assertContents(list, 1)
        val n2 = IntNode(2).apply { list.addLast(this) }
        assertContents(list, 1, 2)
        val n3 = IntNode(3).apply { list.addLast(this) }
        assertContents(list, 1, 2, 3)
        val n4 = IntNode(4).apply { list.addLast(this) }
        assertContents(list, 1, 2, 3, 4)
        assertTrue(n1.remove())
        assertContents(list, 2, 3, 4)
        assertTrue(n3.remove())
        assertContents(list, 2, 4)
        assertTrue(n4.remove())
        assertContents(list, 2)
        assertTrue(n2.remove())
        assertFalse(n2.remove())
        assertContents(list)
    }

    @Test
    fun testCondOps() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        assertTrue(list.addLastIf(IntNode(1)) { true })
        assertContents(list, 1)
        assertFalse(list.addLastIf(IntNode(2)) { false })
        assertContents(list, 1)
        assertTrue(list.addFirstIf(IntNode(3)) { true })
        assertContents(list, 3, 1)
        assertFalse(list.addFirstIf(IntNode(4)) { false })
        assertContents(list, 3, 1)
    }


    @Test
    fun testRemoveTwoAtomic() {
        val list = LockFreeLinkedListHead()
        val n1 = IntNode(1).apply { list.addLast(this) }
        val n2 = IntNode(2).apply { list.addLast(this) }
        assertContents(list, 1, 2)
        assertFalse(n1.isRemoved)
        assertFalse(n2.isRemoved)
        val remove1Desc = n1.describeRemove()!!
        val remove2Desc = n2.describeRemove()!!
        val operation = object : AtomicOpDescriptor() {
            override fun unwrapRef(affected: Any): Any =
                remove1Desc.unwrapRef(this, affected) ?: remove2Desc.unwrapRef(this, affected)!!
            override fun prepare(): Boolean =
                    remove1Desc.prepare(this) && remove2Desc.prepare(this)
            override fun finish(affected: Any?, success: Boolean) {
                remove1Desc.finish(this, success)
                remove2Desc.finish(this, success)
            }
        }
        assertTrue(operation.perform(null))
        assertTrue(n1.isRemoved)
        assertTrue(n2.isRemoved)
        assertContents(list)
    }

    @Test
    fun testAtomicOpsSingle() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        val n1 = IntNode(1).also { single(list.describeAddFirst(it)) }
        assertContents(list, 1)
        val n2 = IntNode(2).also { single(list.describeAddFirst(it)) }
        assertContents(list, 2, 1)
        val n3 = IntNode(3).also { single(list.describeAddLast(it)) }
        assertContents(list, 2, 1, 3)
        val n4 = IntNode(4).also { single(list.describeAddLast(it)) }
        assertContents(list, 2, 1, 3, 4)
        single(n3.describeRemove()!!)
        assertContents(list, 2, 1, 4)
        assertTrue(n3.describeRemove() == null)
        single(list.describeRemoveFirst()!!)
        assertContents(list, 1, 4)
        assertTrue(n2.describeRemove() == null)
    }

    private fun single(part: AtomicOpPart) {
        val operation = object : AtomicOpDescriptor() {
            override fun unwrapRef(affected: Any): Any = part.unwrapRef(this, affected)!!
            override fun prepare(): Boolean = part.prepare(this)
            override fun finish(affected: Any?, success: Boolean) = part.finish(this, success)
        }
        assertTrue(operation.perform(null))
    }

    @Test
    fun testAddFirstAndRemoveAtomic() {
        val list = LockFreeLinkedListHead()
        val n1 = IntNode(1).apply { list.addLast(this) }
        val n2 = IntNode(2).apply { list.addLast(this) }
        assertContents(list, 1, 2)
        assertFalse(n1.isRemoved)
        val removeDesc = n1.describeRemove()!!
        val addDesc = list.describeAddFirst(IntNode(3))
        val operation = object : AtomicOpDescriptor() {
            override fun unwrapRef(affected: Any): Any =
                removeDesc.unwrapRef(this, affected) ?: addDesc.unwrapRef(this, affected)!!
            override fun prepare(): Boolean =
                removeDesc.prepare(this) && addDesc.prepare(this)
            override fun finish(affected: Any?, success: Boolean) {
                removeDesc.finish(this, success)
                addDesc.finish(this, success)
            }
        }
        assertTrue(operation.perform(null))
        assertTrue(n1.isRemoved)
        assertContents(list, 3, 2)
    }

    @Test
    fun testAddLastAndRemoveAtomic() {
        val list = LockFreeLinkedListHead()
        val n1 = IntNode(1).apply { list.addLast(this) }
        val n2 = IntNode(2).apply { list.addLast(this) }
        assertContents(list, 1, 2)
        assertFalse(n1.isRemoved)
        val removeDesc = n1.describeRemove()!!
        val addDesc = list.describeAddLast(IntNode(3))
        val operation = object : AtomicOpDescriptor() {
            override fun unwrapRef(affected: Any): Any =
                removeDesc.unwrapRef(this, affected) ?: addDesc.unwrapRef(this, affected)!!
            override fun prepare(): Boolean =
                removeDesc.prepare(this) && addDesc.prepare(this)
            override fun finish(affected: Any?, success: Boolean) {
                removeDesc.finish(this, success)
                addDesc.finish(this, success)
            }
        }
        assertTrue(operation.perform(null))
        assertTrue(n1.isRemoved)
        assertContents(list, 2, 3)
    }

    private fun assertContents(list: LockFreeLinkedListHead, vararg expected: Int) {
        list.validate()
        val n = expected.size
        val actual = IntArray(n)
        var index = 0
        list.forEach<IntNode> { actual[index++] = it.i }
        assertEquals(n, index)
        for (i in 0 until n) assertEquals("item i", expected[i], actual[i])
        assertEquals(expected.isEmpty(), list.isEmpty)
    }
}