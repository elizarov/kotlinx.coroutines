/*
 * Copyright 2016-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlinx.coroutines.experimental

import kotlin.js.Promise
import kotlin.test.*

class PromiseTest : TestBase() {
    @Test
    fun testPromiseResolvedAsDeferred() = promise {
        val promise = Promise<String> { resolve, _ ->
            resolve("OK")
        }
        val deferred = promise.asDeferred()
        assertEquals("OK", deferred.await())
    }
    
    @Test
    fun testPromiseRejectedAsDeferred() = promise {
        val promise = Promise<String> { _, reject ->
            reject(TestException())
        }
        val deferred = promise.asDeferred()
        try {
            deferred.await()
            expectUnreached()
        } catch (e: Throwable) {
            assertTrue(e is TestException)
        }
    }

    @Test
    fun testCompletedDeferredAsPromise() = promise {
        val deferred = async(coroutineContext, CoroutineStart.UNDISPATCHED) {
            // completed right away
            "OK"
        }
        val promise = deferred.asPromise()
        assertEquals("OK", promise.await())
    }

    @Test
    fun testWaitForDeferredAsPromise() = promise {
        val deferred = async(coroutineContext) {
            // will complete later
            "OK"
        }
        val promise = deferred.asPromise()
        assertEquals("OK", promise.await()) // await yields main thread to deferred coroutine
    }

    @Test
    fun testCancellableAwaitPromise() = promise {
        lateinit var r: (String) -> Unit
        val toAwait = Promise<String> { resolve, _ -> r = resolve }
        val job = launch(coroutineContext, CoroutineStart.UNDISPATCHED) {
            toAwait.await() // suspends
        }
        job.cancel() // cancel the job
        r("fail") // too late, the waiting job was already cancelled
    }

    private class TestException : Exception()
}