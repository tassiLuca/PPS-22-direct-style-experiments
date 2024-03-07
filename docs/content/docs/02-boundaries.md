---
bookToc: false
---

# `boundary` & `break`

- [`boundary` \& `break`](#boundary--break)
  - [Modeling error handling data types with non-local breaks](#modeling-error-handling-data-types-with-non-local-breaks)
    - [`Optional`](#optional)
    - [`Either` + `?`](#either--)

`boundary` & `break` mechanism provides a cleaner alternative to non-local returns:

- `boundary:` is short for `boundary.apply:`
- the indented code below, passed as `body`, is a context function that is called within `boundary.apply`
  - to `break`, an in-scope `given` instance of `Label` is required (i.e. is impossible to `break` without an enclosing `boundary`)
  - Users don't define `Label` instances themselves. Instead, this is done inside the implementation of `boundary.apply` to provide the capability of doing a non-local return [[Ref](https://github.com/lampepfl/dotty/blob/3.3.0-RC4/library/src/scala/util/boundary.scala)]:
    ```scala
    // From the Scala 3 standard library
    /** Run `body` with freshly generated label as implicit argument. 
      * Catch any breaks associated with that label and return their 
      * results instead of `body`'s result.
      */
    inline def apply[T](inline body: Label[T] ?=> T): T =
      val local = Label[T]()
      try body(using local)
      catch case ex: Break[T] @unchecked =>
        if ex.label eq local then ex.value
        else throw ex
    ```
  - non-local breaks are implemented as non-fatal exceptions: the implementation is optimized to suppress unnecessary stack traces (which makes exceptions very slow); stack traces are useless since the exceptions are managed rather than exposed to the user abruptly
    - enhanced performance is achieved when a break occurs within the same method, allowing it to be rewritten as a jump call to the enclosing scope within the same stack frame.

{{< hint info >}}

`boundary` and `break` can be particularly useful for **error handling** (later examples will show some use cases) and **inner loops** where we need a **short exit path**.
But, most importantly, they **lay the foundations** (along with a **`resume` mechanism**) for building new **direct-style concurrency abstractions** based on **suspensions**.

{{< /hint >}}

## Modeling error handling data types with non-local breaks

[[Here you can find the full source](https://github.com/tassiLuca/direct-style-experiments/tree/master/commons/src/main/scala/io/github/tassiLuca/dse/boundaries).]

In the following section are presented two data types that can be used to handle errors, both leveraging the `boundary` and `break` mechanism.
The first (`optional`) has been presented in the [Scalar conference by M. Odersky](https://www.google.com/search?client=safari&rls=en&q=direct+style+odersky&ie=UTF-8&oe=UTF-8), while the second has been implemented to apply the same style also to `Either` data type.

### `Optional`

```scala
/** Represents a computation that will hopefully return 
  * [[Some]]thing or simply [[None]] if it can't. */
object optional:

  /** Defines the boundary for an [[Option]] returning computation,
    * whose [[body]] is given in input. */
  inline def apply[T](inline body: Label[None.type] ?=> T): Option[T] =
    boundary(Some(body))

  extension [T](o: Option[T])
    /** @return the enclosed [[Option]] object if defined, or break 
      * to the enclosing boundary with [[None]]. */
    inline def ?(using label: Label[None.type]): T =
      o.getOrElse(break(None))
```

### `Either` + `?`

```scala
/** Represents a computation that will hopefully return a [[Right]] value but might fail with a [[Left]] one.*/
object either:

  /** Defines the boundary for the [[Either]] returning computation, whose [[body]] is given in input. */
  inline def apply[L, R](inline body: Label[Left[L, Nothing]] ?=> R): Either[L, R] =
    boundary(Right(body))

  /** Quickly break to the enclosing boundary with a [[Left]] filled with [[l]]. */
  inline def left[L, R](l: L)(using Label[Left[L, Nothing]]): Either[L, R] = break(Left(l))

  extension [L, R](e: Either[L, R])
    /** @return this [[Right]] value or break to the enclosing boundary with the [[Left]] value. */
    inline def ?(using Label[Left[L, Nothing]]): R = e match
      case Right(value) => value
      case Left(value) => break(Left(value))

  extension [R](t: Try[R])
    /** @return this [[Success]] value or break to the enclosing boundary with a [[Left]] containing 
      *         the converted `Throwable` exception performed by the implicit [[converter]].
      */
    inline def ?[L](using Label[Left[L, Nothing]])(using converter: Conversion[Throwable, L]): R = 
      t match
        case Success(value) => value
        case Failure(exception) => break(Left(converter(exception)))

/** An object encapsulating a collection of `Throwable` given converters. */
object EitherConversions:

  /** Converts a `Throwable` to a `String` with its message. */
  given Conversion[Throwable, String] = _.getMessage
```

This kind of data type will be particularly useful in the next examples to quickly break in case of failures, returning the caller a meaningful error message, and simplifying the error-handling code.

{{< button relref="/01-overview" >}} **Overview** {{< /button >}}

{{< button relref="/03-basics" >}} **Next**: Basic asynchronous constructs {{< /button >}}
