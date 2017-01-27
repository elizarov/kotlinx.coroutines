package kotlinx.coroutines.experimental.internal

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater

/**
 * The most abstract operation that can be in process. Other threads observing an instance of this
 * class in the fields of their object shall invoke [perform] to help.
 */
internal abstract class OpDescriptor {
    abstract fun unwrapRef(affected: Any): Any
    abstract fun perform(affected: Any?): Boolean
}

/**
 * Descriptor for multi-word atomic operation.
 * Based on paper
 * ["A Practical Multi-Word Compare-and-Swap Operation"](http://www.cl.cam.ac.uk/research/srg/netos/papers/2002-casn.pdf)
 * by Timothy L. Harris, Keir Fraser and Ian A. Pratt.
 */
internal abstract class AtomicOpDescriptor : OpDescriptor() {
    @Volatile
    private var _consensus: Int = UNDECIDED // status of operation

    companion object {
        @JvmStatic
        private val CONSENSUS: AtomicIntegerFieldUpdater<AtomicOpDescriptor> =
                AtomicIntegerFieldUpdater.newUpdater(AtomicOpDescriptor::class.java, "_consensus")

        const val UNDECIDED = 0
        const val SUCCESS = 1
        const val FAILURE = 2
    }

    val consensus: Int get() = _consensus

    abstract fun prepare(): Boolean // result matters only for undecided operation!
    abstract fun finish(affected: Any?, success: Boolean)

    final override fun perform(affected: Any?): Boolean {
        // make decision on status
        var consensus: Int
        while (true) {
            consensus = this._consensus
            if (consensus != UNDECIDED) break
            val proposal = if (prepare()) SUCCESS else FAILURE
            if (CONSENSUS.compareAndSet(this, UNDECIDED, proposal)) {
                consensus = proposal
                break
            }
        }
        val success = consensus == SUCCESS
        finish(affected, success)
        return success
    }

}

// a part of multi-step operation
internal abstract class AtomicOpPart {
    abstract fun unwrapRef(op: AtomicOpDescriptor, affected: Any): Any?
    abstract fun prepare(op: AtomicOpDescriptor): Boolean
    abstract fun finish(op: AtomicOpDescriptor, success: Boolean)
}
