package examples

import kotlinx.coroutines.experimental.future.await
import kotlinx.coroutines.experimental.future.future
import java.util.concurrent.CompletableFuture

fun main(args: Array<String>) {
    // this example shows how easy it is to perform multiple async operations with coroutines
    val future = future {
        (1..5).map { // loops are no problem at all
            startLongAsyncOperation(it).await() // suspend while the long method is running
        }.joinToString("\n")
    }
    println("We have a long-running computation in background, let's wait for its result...")
    println(future.get())
}

fun startLongAsyncOperation(num: Int): CompletableFuture<String> =
    CompletableFuture.supplyAsync {
        Thread.sleep(1000L) // imitate some long-running computation, actually
        "$num" // and return a number converted to string
    }
