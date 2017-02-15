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

package kotlinx.coroutines.experimental.rx1

import kotlinx.coroutines.experimental.TestBase
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test

class JointTest : TestBase() {
    @Test
    fun testJoinSingle() = runBlocking<Unit> {
        expect(1)
        val single = rxSingle(context) {
            expect(3)
            "OK"
        }
        expect(2)
        single.join()
        finish(4)
    }

    @Test
    fun testJoinSingleFail() = runBlocking<Unit> {
        expect(1)
        val single = rxSingle(context) {
            expect(3)
            throw RuntimeException("Fail")
        }
        expect(2)
        single.join()
        finish(4)
    }

    @Test
    fun testJoinObservable() = runBlocking<Unit> {
        expect(1)
        val observable = rxObservable(context) {
            expect(3)
            send("O")
            expect(4)
            send("K")
            expect(5)
        }
        expect(2)
        observable.join()
        finish(6)
    }

    @Test
    fun testJoinObservableFail() = runBlocking<Unit> {
        expect(1)
        val observable = rxObservable(context) {
            expect(3)
            send("O")
            expect(4)
            send("K")
            expect(5)
            throw RuntimeException("Fail")
        }
        expect(2)
        observable.join()
        finish(6)
    }
}