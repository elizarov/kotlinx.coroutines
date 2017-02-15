# Module kotlinx-coroutines-rx1

Utilities for [RxJava 1.x](https://github.com/ReactiveX/RxJava/tree/1.x).

Coroutine builders:

| **Name**       | **Result**                  | **Scope**        | **Description**
| -------------- | --------------------------- | ---------------- | ---------------
| [rxSingle]     | [Single][io.reactivex.Single]         | [CoroutineScope] | Cold single that starts coroutine on subscribe
| [rxObservable] | [Observable][io.reactivex.Observable] | [ProducerScope]  | Cold observable that starts coroutine on subscribe

Suspending extension functions and suspending iteration:

| **Name** | **Description**
| -------- | ---------------
| [Single.await][io.reactivex.Single.await] | Awaits for completion of the single value 
| [Observable.awaitFirst][io.reactivex.Observable.awaitFirst] | Awaits for the first value from the given observable
| [Observable.awaitLast][io.reactivex.Observable.awaitFirst] | Awaits for the last value from the given observable
| [Observable.awaitSingle][io.reactivex.Observable.awaitSingle] | Awaits for the single value from the given observable
| [Observable.open][io.reactivex.Observable.open] | Subscribes to observable and returns [ReceiveChannel] 
| [Observable.iterator][io.reactivex.Observable.iterator] | Subscribes to observable and returns [ChannelIterator]

Conversion functions:

| **Name** | **Description**
| -------- | ---------------
| [Deferred.toSingle][kotlinx.coroutines.experimental.Deferred.toSingle] | Converts deferred value to hot single
| [ReceiveChannel.toObservable][kotlinx.coroutines.experimental.channels.ReceiveChannel.toObservable] | Converts streaming channel to hot observable

<!--- SITE_ROOT https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core -->
<!--- DOCS_ROOT kotlinx-coroutines-core/target/dokka/kotlinx-coroutines-core -->
<!--- INDEX kotlinx.coroutines.experimental -->
[CoroutineScope]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/index.html
<!--- INDEX kotlinx.coroutines.experimental.channels -->
[ChannelIterator]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-channel-iterator/index.html
[ProducerScope]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-producer-scope/index.html
[ReceiveChannel]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-receive-channel/index.html
<!--- SITE_ROOT https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx2 -->
<!--- DOCS_ROOT kotlinx-coroutines-rx2/target/dokka/kotlinx-coroutines-rx2 -->
<!--- INDEX kotlinx.coroutines.experimental.rx2 -->
[kotlinx.coroutines.experimental.Deferred.toSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/kotlinx.coroutines.experimental.-deferred/to-single.html
[kotlinx.coroutines.experimental.channels.ReceiveChannel.toObservable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/kotlinx.coroutines.experimental.channels.-receive-channel/to-observable.html
[io.reactivex.Observable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/index.html
[io.reactivex.Observable.awaitFirst]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/await-first.html
[io.reactivex.Observable.awaitSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/await-single.html
[io.reactivex.Observable.iterator]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/iterator.html
[io.reactivex.Observable.open]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/open.html
[io.reactivex.Single]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-single/index.html
[io.reactivex.Single.await]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-single/await.html
[rxObservable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx-observable.html
[rxSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx-single.html
<!--- END -->

# Package kotlinx.coroutines.experimental.rx2

Utilities for [RxJava 1.x](https://github.com/ReactiveX/RxJava/tree/1.x).
