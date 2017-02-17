package kotlinx.coroutines.experimental.intrinsics

import kotlin.coroutines.experimental.Continuation

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
internal fun <R> (suspend () -> R).startUndispatchedCoroutine(completion: Continuation<R>) {
    val value = try {
        (this as kotlin.jvm.functions.Function1<Continuation<R>, Any?>).invoke(completion)
    } catch (e: Throwable) {
        completion.resumeWithException(e)
        return
    }
    completion.resume(value as R)
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
internal fun <E, R> (suspend (E) -> R).startUndispatchedCoroutine(element: E, completion: Continuation<R>) {
    val value = try {
        (this as kotlin.jvm.functions.Function2<E, Continuation<R>, Any?>).invoke(element, completion)
    } catch (e: Throwable) {
        completion.resumeWithException(e)
        return
    }
    completion.resume(value as R)
}
