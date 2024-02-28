package io.github.tassiLuca.analyzer.lib

import gears.async.Async
import io.github.tassiLuca.analyzer.commons.lib.{Contribution, Release, Repository}

import scala.annotation.tailrec

trait GitHubService:
  def repositoriesOf(organizationName: String)(using Async): Either[String, Seq[Repository]]
  def contributorsOf(organizationName: String, repositoryName: String)(using Async): Either[String, Seq[Contribution]]
  def lastReleaseOf(organizationName: String, repositoryName: String)(using Async): Either[String, Release]

object GitHubService:
  def apply(): GitHubService = GitHubServiceImpl()

  private class GitHubServiceImpl extends GitHubService:
    import sttp.model.Uri
    import sttp.client3.{SimpleHttpClient, UriContext, basicRequest, Response}
    import upickle.default.{read, Reader}

    private val baseUrl = "https://api.github.com"
    private val request = basicRequest.auth.bearer(System.getenv("GH_TOKEN"))

    override def repositoriesOf(
        organizationName: String,
    )(using Async): Either[String, Seq[Repository]] =
      paginatedRequest[Repository](uri"$baseUrl/orgs/$organizationName/repos")

    override def contributorsOf(
        organizationName: String,
        repositoryName: String,
    )(using Async): Either[String, Seq[Contribution]] =
      paginatedRequest[Contribution](uri"$baseUrl/repos/$organizationName/$repositoryName/contributors")

    override def lastReleaseOf(
        organizationName: String,
        repositoryName: String,
    )(using Async): Either[String, Release] =
      plainRequest[Release](uri"$baseUrl/repos/$organizationName/$repositoryName/releases/latest")

    private def plainRequest[T](endpoint: Uri)(using Reader[T]): Either[String, T] =
      doRequest(endpoint).body.map(read[T](_))

    private def paginatedRequest[T](endpoint: Uri)(using Reader[T]): Either[String, Seq[T]] =
      @tailrec
      def withPagination(partialResponse: Either[String, Seq[T]])(next: Option[Uri]): Either[String, Seq[T]] =
        next match
          case None => partialResponse
          case Some(uri) =>
            val response = doRequest(uri)
            val next = nextPage(response)
            withPagination(partialResponse.flatMap(pr => response.body.map(pr ++ read[Seq[T]](_))))(next)
      withPagination(Right(Seq[T]()))(Some(endpoint))

    private def doRequest(endpoint: Uri): Response[Either[String, String]] =
      SimpleHttpClient().send(request.get(endpoint))

    private def nextPage(response: Response[Either[String, String]]): Option[Uri] = response
      .headers("Link")
      .flatMap(_.split(","))
      .find(_.contains("rel=\"next\""))
      .map(_.takeWhile(_ != ';').trim.stripPrefix("<").stripSuffix(">"))
      .flatMap(Uri.parse(_).toOption)