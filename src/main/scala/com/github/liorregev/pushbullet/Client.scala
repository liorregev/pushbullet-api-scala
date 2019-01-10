package com.github.liorregev.pushbullet

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import akka.util.ByteString
import cats.syntax.either._
import ch.qos.logback.classic.LoggerContext
import com.github.liorregev.pushbullet.domain.{DomainObject, Operation, Request, Response}
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

sealed trait ClientError
final case class ResponseParseError(jsError: JsError) extends ClientError
object ResponseParseError {
  def apply(errors: Seq[(JsPath, Seq[JsonValidationError])]): ResponseParseError = new ResponseParseError(JsError(errors))
}
final case class HTTPError(response: HttpResponse) extends ClientError

class Client(baseUrl: Uri, apiKey: String)(implicit system: ActorSystem, loggerFactory: LoggerContext, materializer: ActorMaterializer) {
  private lazy val logger = loggerFactory.getLogger(getClass)
  private lazy val http = Http()

  def request[Obj <: DomainObject, Resp <: Response[Obj]](req: Request[Obj, Resp])
                                                         (implicit ec: ExecutionContext): Future[Either[ClientError, Resp]] = {
    logger.info(s"Processing $req")
    runRequest(req)
      .flatMap {
        response =>
          if(response.status.isSuccess()) {
            response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.decodeString("utf-8").asRight[ClientError])
          } else {
            Future(Left[ClientError, String](HTTPError(response)))
          }
      }
      .map{
        response =>
          for {
            body <- response
            parsedBody <- Json.parse(body).validate[JsObject].asEither.leftMap[ClientError](ResponseParseError.apply)
            relevantPart = parsedBody.value.get(req.objName).flatMap(_.validate[JsObject].asOpt).getOrElse(parsedBody)
            result <- req.responseReads.reads(parsedBody).asEither
              .leftFlatMap(_ => req.responseReads.reads(relevantPart).asEither)
              .leftMap[ClientError](ResponseParseError.apply)
          } yield result
      }
  }

  private def runRequest[Obj <: DomainObject, Resp <: Response[Obj]](req: Request[Obj, Resp]): Future[HttpResponse] = {
    logger.debug(s"Sending request $req")
    val baseRequest = HttpRequest()
      .withHeaders(
        RawHeader("Access-Token", apiKey),
        `Content-Type`(ContentTypes.`application/json`),
      )
      .withMethod(req.op.method)

    val finalRequest = req.op match {
      case Operation.Create =>
        baseRequest
          .withEntity(HttpEntity(ContentTypes.`application/json`, req.toJson.toString))
          .withUri(baseUrl.withPath(baseUrl.path / req.objName))
      case Operation.Delete(iden) =>
        baseRequest
          .withUri(baseUrl.withPath(baseUrl.path / req.objName / iden))
      case Operation.Update(iden) =>
        baseRequest
          .withUri(baseUrl.withPath(baseUrl.path / req.objName / iden))
          .withEntity(HttpEntity(ContentTypes.`application/json`, req.toJson.toString))
      case Operation.List(params) =>
        baseRequest
          .withUri(baseUrl.withPath(baseUrl.path / req.objName).withQuery(Uri.Query(params)))
    }
    http.singleRequest(finalRequest)
  }
}
