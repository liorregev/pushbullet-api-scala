package com.github.liorregev.pushbullet

import com.github.liorregev.pushbullet.domain.{Request, DomainObject, Response}

import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.either._
import ch.qos.logback.classic.LoggerContext
import com.softwaremill.sttp._
import com.softwaremill.sttp.{Response => STTPResponse}
import play.api.libs.json._

sealed trait ClientError
final case class ResponseParseError(jsError: JsError) extends ClientError
final case class HTTPError(response: STTPResponse[String]) extends ClientError

class Client(url: Uri)(implicit wsClient: SttpBackend[Future, Nothing], loggerFactory: LoggerContext) {
  private lazy val logger = loggerFactory.getLogger(getClass)

  def request[T <: DomainObject, C[_], Resp <: Response[T, C]](req: Request[T, C, Resp])
                                                     (implicit writes: Writes[Request[T, C, Resp]],
                                                     ec: ExecutionContext): Future[Either[ClientError, Resp]] = {
    logger.info(s"Processing $req")
    runRequest(req)
      .map(response => response.body
          .leftMap[ClientError](_ => HTTPError(response))
          .flatMap { body =>
            req.responseReads.reads(Json.parse(body))
              .asEither
              .leftMap(errors => ResponseParseError(JsError(errors)))
          }
      )
  }

  private def runRequest[T <: DomainObject, C[_], Resp <: Response[T, C]](req: Request[T, C, Resp])
                                                                (implicit writes: OWrites[Request[T, C, Resp]],
                                                                 ec: ExecutionContext): Future[STTPResponse[String]] = {
    logger.debug(s"Sending request $req")
    sttp
      .body(writes.writes(req).toString)
      .method(req.op.method, url)
      .send()
  }
}
