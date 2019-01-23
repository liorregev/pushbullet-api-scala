package com.github.liorregev.pushbullet

import java.io.File
import java.nio.file.Files
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import cats.syntax.either._
import ch.qos.logback.classic.LoggerContext
import com.github.liorregev.pushbullet.domain.{AllItems, DomainObject, Operation, PushListRequest, Request, Response, SingleItem}
import com.github.liorregev.pushbullet.listener.{Handler, Listener, ListenerError, ProtoMessage}
import com.github.liorregev.pushbullet.serialization._
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

sealed trait ClientError
final case class ResponseParseError(jsError: JsError) extends ClientError
object ResponseParseError {
  def apply(errors: Seq[(JsPath, Seq[JsonValidationError])]): ResponseParseError = new ResponseParseError(JsError(errors))
}
final case class HTTPError(response: HttpResponse) extends ClientError

final case class UploadFileResponse(fileName: String, fileType: String, fileUrl: String, uploadUrl: String)
object UploadFileResponse {
  implicit val format: OFormat[UploadFileResponse] = snakeCaseFormat(Json.format)
}

class Client(apiKey: String)(implicit system: ActorSystem, loggerFactory: LoggerContext, materializer: ActorMaterializer) {
  private lazy val logger = loggerFactory.getLogger(getClass)
  private lazy val http = Http()
  private val baseUrl = Uri("https://api.pushbullet.com/v2")

  final def request[Obj <: DomainObject, Resp <: Response[Obj]](req: Request[Obj, Resp])
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

  def loadLatestServerTime()(implicit ec: ExecutionContext): Future[Instant] = this.request(PushListRequest())
    .flatMap(_.fold(_ => loadLatestServerTime(),
      resp => Future.successful(resp.pushes.headOption.map(_.baseInfo.modified).getOrElse(Instant.now()))))

  private def runRequest[Obj <: DomainObject, Resp <: Response[Obj]](req: Request[Obj, Resp]): Future[HttpResponse] = {
    val baseRequest = HttpRequest()
      .withHeaders(RawHeader("Access-Token", apiKey))
      .withMethod(req.op.method)

    val finalRequest = req.op match {
      case Operation.Create =>
        baseRequest
          .withEntity(HttpEntity(ContentTypes.`application/json`, req.toJson.toString))
          .withUri(baseUrl.withPath(baseUrl.path / req.objName))
      case Operation.Delete(SingleItem(iden)) =>
        baseRequest
          .withUri(baseUrl.withPath(baseUrl.path / req.objName / iden))
      case Operation.Delete(AllItems) =>
        baseRequest
          .withUri(baseUrl.withPath(baseUrl.path / req.objName))
      case Operation.Update(SingleItem(iden)) =>
        baseRequest
          .withUri(baseUrl.withPath(baseUrl.path / req.objName / iden))
          .withEntity(HttpEntity(ContentTypes.`application/json`, req.toJson.toString))
      case Operation.List(params) =>
        baseRequest
          .withUri(baseUrl.withPath(baseUrl.path / req.objName).withQuery(Uri.Query(params)))
      case Operation.Get(what) =>
        baseRequest
          .withUri(baseUrl.withPath(baseUrl.path / req.objName / what))
    }
    http.singleRequest(finalRequest)
  }

  final def uploadFile(fileName: String, fileType: ContentType, file: File)
                      (implicit ec: ExecutionContext): Future[Either[ClientError, UploadFileResponse]] = {
    val requestData = Map(
      "file_name" -> JsString(fileName),
      "file_type" -> JsString(fileType.toString)
    )
    val request = HttpRequest()
      .withHeaders(RawHeader("Access-Token", apiKey))
      .withEntity(HttpEntity(ContentTypes.`application/json`, JsObject(requestData).toString))
      .withUri(baseUrl.withPath(baseUrl.path / "upload-request"))
      .withMethod(HttpMethods.POST)
    http.singleRequest(request)
      .flatMap {
        response =>
          if(response.status.isSuccess()) {
            response
              .entity
              .dataBytes
              .runFold(ByteString(""))(_ ++ _)
              .map(_.decodeString("utf-8").asRight[ClientError])
          } else {
            Future.successful(Left[ClientError, String](HTTPError(response)))
          }
      }
      .map {
        response =>
          for {
            body <- response
            uploadResponse <- Json.parse(body).validate[UploadFileResponse].asEither.leftMap[ClientError](ResponseParseError.apply)
          } yield uploadResponse
      }
      .flatMap(
        _.fold(err => Future.successful(err.asLeft[UploadFileResponse]), resp => doUploadFile(resp, file))
      )
  }

  private def doUploadFile(uploadFileResponse: UploadFileResponse, file: File)
                          (implicit ec: ExecutionContext): Future[Either[ClientError, UploadFileResponse]] = {
    val contentType = ContentType(MediaType.customMultipart("form-data", Map.empty))
    val entity =
      Multipart.FormData.Strict(
        List(
          Multipart.FormData.BodyPart.Strict(
            uploadFileResponse.fileName,
            HttpEntity.Strict(contentType, ByteString.apply(Files.readAllBytes(file.toPath)))
          )
        )
      )
      .toEntity()

    val request = HttpRequest()
      .withHeaders(
        RawHeader("Access-Token", apiKey)
      )
      .withEntity(entity)
      .withUri(Uri(uploadFileResponse.uploadUrl))
      .withMethod(HttpMethods.POST)
    http.singleRequest(request)
      .map {
        response =>
          if(response.status.isSuccess()) {
            uploadFileResponse.asRight[ClientError]
          } else {
            HTTPError(response).asLeft[UploadFileResponse]
          }
      }
  }

  def startListening(handler: Handler)(implicit ec: ExecutionContext): (() => Unit, Future[Either[ListenerError, ProtoMessage]]) = {
    val listener = new Listener(this, handler)
    val flow = Flow.fromGraph(listener.processGraph)

    val webSocketFlow = http.webSocketClientFlow(WebSocketRequest(s"wss://stream.pushbullet.com/websocket/$apiKey"))
    val (finishPromise, done) = Source.maybe
      .viaMat(webSocketFlow)(Keep.left)
      .viaMat(flow)(Keep.left)
      .idleTimeout(1 minute)
      .toMat(Sink.last)(Keep.both)
      .run()
    val stopListening: () => Unit = () => finishPromise.success(None)
    (stopListening, done)
  }
}
