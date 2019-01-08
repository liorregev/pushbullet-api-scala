package com.github.liorregev.pushbullet

import cats.syntax.either._
import ch.qos.logback.classic.LoggerContext
import com.github.liorregev.pushbullet.domain.{Operations, Request, Response}
import com.softwaremill.sttp.{Response => STTPResponse, _}
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

sealed trait ClientError
final case class ResponseParseError(jsError: JsError) extends ClientError
object ResponseParseError {
  def apply(errors: Seq[(JsPath, Seq[JsonValidationError])]): ResponseParseError = new ResponseParseError(JsError(errors))
}

final case class HTTPError(response: STTPResponse[String]) extends ClientError

class Client(url: Uri, apiKey: String)(implicit wsClient: SttpBackend[Future, Nothing], loggerFactory: LoggerContext) {
  private lazy val logger = loggerFactory.getLogger(getClass)

  def request[Resp <: Response[_], Req <: Request[_, Resp]](req: Req)
                                                           (implicit ec: ExecutionContext, writes: Writes[Req]): Future[Either[ClientError, Resp]] = {
    logger.info(s"Processing $req")
    runRequest[Resp, Req](req)
      .map{
        response =>
          for {
            body <- response.body.leftMap[ClientError](_ => HTTPError(response))
            parsedBody <- Json.parse(body).validate[JsObject].asEither.leftMap[ClientError](ResponseParseError.apply)
            relevantPart = parsedBody.value.get(req.objName).flatMap(_.validate[JsObject].asOpt).getOrElse(parsedBody)
            result <- req.responseReads.reads(parsedBody).asEither
              .leftFlatMap(_ => req.responseReads.reads(relevantPart).asEither)
              .leftMap[ClientError](ResponseParseError.apply)
          } yield result
      }
  }

  private def runRequest[Resp <: Response[_], Req <: Request[_, Resp]](req: Req)
                                                                (implicit writes: Writes[Req]): Future[STTPResponse[String]] = {
    logger.debug(s"Sending request $req")
    val baseRequest = sttp
      .headers(
        "Access-Token" -> apiKey,
        "Content-Type" -> "application/json"
      )
      .method(req.op.method, url.path(url.path :+ req.objName))

    val finalRequest = req.op match {
      case Operations.Create =>
        baseRequest
          .body(writes.writes(req).toString)
      case Operations.Delete(iden) =>
        baseRequest
          .method(req.op.method, url.path(url.path ++ Seq(req.objName, iden)))
      case Operations.Update(iden) =>
        baseRequest
          .method(req.op.method, url.path(url.path ++ Seq(req.objName, iden)))
          .body(writes.writes(req).toString)
      case Operations.List =>
        baseRequest
    }
    finalRequest.send()
  }
}
