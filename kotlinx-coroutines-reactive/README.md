# Module kotlinx-coroutines-reactive

Utilities for [Reactive Streams](http://www.reactive-streams.org).

Coroutine builders:

| **Name**        | **Result**                    | **Scope**        | **Description**
| --------------- | ----------------------------- | ---------------- | ---------------
| [publish]       | [Publisher][org.reactivestreams.Publisher] | [ProducerScope] | Cold reactive publisher that starts coroutine on subscribe

Suspending extension functions and suspending iteration:

| **Name** | **Description**
| -------- | ---------------
| [Publisher.awaitFirst][org.reactivestreams.Publisher.awaitFirst] | Returns the first value from the given publisher
| [Publisher.awaitLast][org.reactivestreams.Publisher.awaitFirst] | Returns the last value from the given publisher
| [Publisher.awaitSingle][org.reactivestreams.Publisher.awaitSingle] | Returns the single value from the given publisher
| [Publisher.open][org.reactivestreams.Publisher.open] | Subscribes to publisher and returns [ReceiveChannel] 
| [Publisher.iterator][org.reactivestreams.Publisher.iterator] | Subscribes to publisher and returns [ChannelIterator]

Conversion functions:

| **Name** | **Description**
| -------- | ---------------
| [ReceiveChannel.toPublisher][kotlinx.coroutines.experimental.channels.ReceiveChannel.toPublisher] | Converts streaming channel to hot publisher

<!--- SITE_ROOT https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core -->
<!--- DOCS_ROOT kotlinx-coroutines-core/target/dokka/kotlinx-coroutines-core -->
<!--- INDEX kotlinx.coroutines.experimental -->
[CoroutineScope]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/index.html
<!--- INDEX kotlinx.coroutines.experimental.channels -->
[ChannelIterator]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-channel-iterator/index.html
[ProducerScope]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-producer-scope/index.html
[ReceiveChannel]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-receive-channel/index.html
<!--- SITE_ROOT https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1 -->
<!--- DOCS_ROOT kotlinx-coroutines-rx1/target/dokka/kotlinx-coroutines-rx1 -->
<!--- INDEX kotlinx.coroutines.experimental.reactive -->
[kotlinx.coroutines.experimental.Deferred.toSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/kotlinx.coroutines.experimental.-deferred/to-single.html
[kotlinx.coroutines.experimental.channels.ReceiveChannel.toObservable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/kotlinx.coroutines.experimental.channels.-receive-channel/to-observable.html
[rx.Observable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx.-observable/index.html
[org.reactivestreams.Publisher.awaitFirst]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx.-observable/await-first.html
[org.reactivestreams.Publisher.awaitSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx.-observable/await-single.html
[org.reactivestreams.Publisher.iterator]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx.-observable/iterator.html
[org.reactivestreams.Publisher.open]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx.-observable/open.html
[rx.Single]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx.-single/index.html
[rx.Single.await]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx.-single/await.html
[rxObservable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx-observable.html
[rxSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx-single.html
<!--- END -->

# Package kotlinx.coroutines.experimental.reactive

Utilities for [Reactive Streams](http://www.reactive-streams.org).
