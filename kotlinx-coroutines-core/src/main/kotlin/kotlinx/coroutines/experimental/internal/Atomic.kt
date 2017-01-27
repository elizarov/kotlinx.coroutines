package kotlinx.coroutines.experimental.internal

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater

/**
 * Descriptor for multi-word atomic operation.
 * Based on paper
 * ["A Practical Multi-Word Compare-and-Swap Operation"](http://www.cl.cam.ac.uk/research/srg/netos/papers/2002-casn.pdf)
 * by Timothy L. Harris, Keir Fraser and Ian A. Pratt.
 */
internal abstract class AtomicOperationDescriptor {
    @Volatile
    private var consensus: Int = UNDECIDED // status of operation

    private companion object {
        @JvmStatic
        val CONSENSUS: AtomicIntegerFieldUpdater<AtomicOperationDescriptor> =
                AtomicIntegerFieldUpdater.newUpdater(AtomicOperationDescriptor::class.java, "consensus")

        const val UNDECIDED = 0
        const val SUCCESS = 1
        const val FAILURE = 2
    }

    abstract fun prepare(): Boolean
    abstract fun finish(node: Any?, success: Boolean)

    fun perform(node: Any? = null): Boolean {
        // make decision on status
        var consensus: Int
        while (true) {
            consensus = this.consensus
            if (consensus != UNDECIDED) break
            val proposal = if (prepare()) SUCCESS else FAILURE
            if (CONSENSUS.compareAndSet(this, UNDECIDED, proposal)) {
                consensus = proposal
                break
            }
        }
        val success = consensus == SUCCESS
        finish(node, success)
        return success
    }

}

// a part of multi-step operation
internal abstract class AtomicOperationPart {
    abstract fun prepare(operation: AtomicOperationDescriptor): Boolean
    abstract fun finish(operation: AtomicOperationDescriptor, success: Boolean)
}
