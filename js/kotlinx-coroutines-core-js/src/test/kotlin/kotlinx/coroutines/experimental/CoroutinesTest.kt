package kotlinx.coroutines.experimental

import kotlin.test.Test

class CoroutinesTest : TestBase() {
    @Test
    fun testWaitChild2() = runTest {
        println("[1]")
        expect(1)
        launch(coroutineContext) {
            println("[3]")
            expect(3)
            yield() // to parent
            println("[5]")
            finish(5)
        }
        println("[2]")
        expect(2)
        yield()
        println("[4]")
        expect(4)
        // parent waits for child's completion
    }
}