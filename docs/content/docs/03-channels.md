# Channels as a communication primitive

The fourth, yet not mentioned, abstraction of both Kotlin Coroutines and Scala Gears is the **channel**.
Channels represent the primitive communication and coordination means to exchange `Future` results. They are, at least conceptually, very similar to a queue where it is possible to send (and receive) data -- basically, exploiting the ***producer-consumer*** pattern.

{{< mermaid >}}
classDiagram
  class `SendableChannel[-T]` {
    << trait >>
    +sendSource(x: T) Async.Source[Either[Closed, Unit]]
    +send(x: T)(using Async) Unit
  }

  class `ReadableChannel[+T]` {
    << trait >>
    +readSource Async.Source[Either[Closed, T]]
    +read()(using Async) Either[Closed, T]
  }

  class `Channel[T]` {
    << trait >>
    +asSendable: SendableChannel[T]
    +asReadable: ReadableChannel[T]
    +asCloseable: java.io.Closeable
  }

  namespace java io {
    class Closeable {
      << interface >>
      +close()
    }
  }

  `SendableChannel[-T]` <|-- `Channel[T]`
  Closeable <|-- `Channel[T]`
  `ReadableChannel[+T]` <|-- `Channel[T]`
{{< /mermaid >}}

The channel is defined through three distinct interfaces: `SendableChannel[-T]`, `ReadableChannel[+T]` and `Channel[T]`, where the latter extends from both `SendableChannel` and `ReadableChannel`. Typically, a `Channel` is created and a `SendableChannel` and `ReadableChannel` instances are respectively provided to the producer and the consumer, restricting their access to it. The same design is present also in Kotlin Coroutines.

Moreover, `Channel` inherits from `java.io.Closable`, making them closable objects: once closed, they raise `ChannelClosedException` when attempting to write to it and immediately return a `Failure(ChannelClosedException)` when attempting to read from it.

Three types of channels exist:

- **Synchronous Channels**: links a read request with a send request within a _rendezvous_
  - `send` (`read`) suspend the process until a consumer `read` (`send`) the value;
- **Buffered Channels**: a version of a channel with an internal buffer of fixed size
  - `send` suspend the producer process if it is full; otherwise, it appends the value to the buffer, returning immediately;
  - `read` suspend if the channel is empty, waiting for a new value.
- **Unbounded Channels**: a version of a channel with an unbounded buffer
  - if the programs run out of memory you can get an out-of-memory exception!

> Multiple producers can send data to the channel, as well as multiple consumers can read them, **but each element is handled only _once_, by _one_ of them**, i.e. consumers **compete** with each other for sent values. Once the element is handled, it is immediately removed from the channel.

## GitHub organization analyzer example

To show channels in action an example has been prepared:

{{< hint info >}}
**Idea**: we want to realize a little asynchronous library allowing clients to collect the common statistics about repositories (issues, stars, last release) and contributors of a given GitHub organization.
{{< /hint >}}

Final result:

![expected result](../../res/img/analyzer-e2e.png)

As usual, the example has been implemented using monadic `Future`s, as well as Scala gears and Kotlin Coroutines.

### Analyzer and App Controller

The direct version in Scala gears exposes the following interface, taking in input an organization name and a function through which is possible to react to results while they are computed.

```scala
trait Analyzer:
  def analyze(organizationName: String)(
      updateResults: RepositoryReport => Unit,
  )(using Async): Either[String, Seq[RepositoryReport]]
```

```scala
object Analyzer:
  def ofGitHub: Analyzer = GitHubAnalyzer()

  private class GitHubAnalyzer extends Analyzer:
    private val gitHubService = GitHubService()

    override def analyze(organizationName: String)(
        updateResults: RepositoryReport => Unit,
    )(using Async): Either[String, Seq[RepositoryReport]] = either:
      val reposInfo = gitHubService
        .repositoriesOf(organizationName).? // 1
        .map(_.performAnalysis) // 2
      val collector = Collector[RepositoryReport](reposInfo.toList*) // 3
      for _ <- reposInfo.indices do 
        updateResults(collector.results.read().tryable.?.awaitResult.?) // 4
      reposInfo.map(_.await)

    extension (r: Repository)
      private def performAnalysis(using Async): Future[RepositoryReport] = ???
```

1. first, we get all the repositories of the requested organization
2. for each of them, the contributors and the last release are retrieved concurrently, starting a `Future`
3. `Future` results are gathered inside a `Collector` that collects a list of futures into a channel of futures, arriving as they finish.
4. read results from the channel as they come, calling the `updateResult` reaction function.

The application controller just takes care of running the application view and, whenever a new analysis starts, creates a new future, during which the single reports are aggregated and the UI is updated.
Thanks to the fact `Future`s are cancellable objects, whenever desired is possible to cancel the current computation.

```scala
private class DirectAppController(using Async) extends AppController:
  private val view = AnalyzerView.gui(this)
  private val analyzer = Analyzer.ofGitHub
  private var currentComputation: Option[Future[Unit]] = None

  view.run()

  override def runSession(organizationName: String): Unit =
    var organizationReport: OrganizationReport = (Map(), Set())
    val f = Future:
      analyzer.analyze(organizationName) { report =>
        organizationReport = (organizationReport._1.aggregatedTo(report), organizationReport._2 + report)
        view.update(organizationReport)
      } match { case Left(e) => view.error(e); case Right(_) => view.endComputation() }
    currentComputation = Some(f)

  override def stopSession(): Unit = currentComputation.foreach(_.cancel())

  extension (m: Map[String, Long])
    private def aggregatedTo(report: RepositoryReport): Map[String, Long] =
      m ++ report.contributions.map(c => c.user -> (m.getOrElse(c.user, 0L) + c.contributions))
```

The Kotlin version with Coroutines is pretty identical to the Gears one.

### GitHub service

One point of difference between the two frameworks is, however, how the `GitHubService` can be implemented, regarding the case where, not just a single, but a multitude of values, are expected.

Indeed, the GitHub API, like many ReSTful APIs, implements _pagination_: if the response includes many results, they are paginated, returning a subset of them; it is the responsibility of the client to request more data (pages).

This can lead to performance issues if the service is implemented to return the whole results in one shot. It would be desirable, instead, to start performing the analysis as soon as one page is obtained from the API.

Kotlin Coroutines offers, for this purpose, the abstraction of **Flow**s, which are conceptually very similar to **cold `Observable`s** in reactive programming.

With respect to reactive programming, they are still quite less reach in terms of operators.

---

- Channels in Kotlin (w.r.t. gears)
  - fairness (also in Gears?)
  - pipeline (not supported in Gears)
  - better closable

---

Points of difference between the gears and Kotlin Coroutines channels are the following:

- closing a channel in gears leads to all subsequent reads returning a `Left(Closed)` preventing the consumer from finishing reading all the values sent on the channel before its closing. This is not the case for Kotlin Coroutines where closing a channel indicates that no more values are coming, but doesn't prevent consuming already sent values. Moreover, in Kotlin is possible to use a regular for loop to receive elements from a channel:
  - [example code in kotlin]
  - The same behavior can be achieved also in gears extending the framework with the concept of `Terminable` channel. After all, closing a channel in coroutines (or terminating it in gears) is a matter of sending a special token to the channel: the iteration stops as soon as this token is received

---

## Conclusions