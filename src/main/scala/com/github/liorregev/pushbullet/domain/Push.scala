package com.github.liorregev.pushbullet.domain

import java.time.Instant

import cats.data.NonEmptyList
import com.github.liorregev.pushbullet.serialization._
import play.api.libs.json._

sealed trait Push extends DomainObject {
  override final val name: String = "pushes"
}
object Push {
  implicit val format: OFormat[Push] = domainObjectFormat(Json.format[ActivePush], Json.format[InactivePush])
}

sealed trait PushData {
  def body: String
}
object PushData {
  final case class ImageData(imageUrl: String, imageWidth: Long, imageHeight: Long)
  final case class FileData(fileName: String, fileType: String, fileUrl:String, imageData: Option[ImageData])
  implicit val imageDataFormat: OFormat[ImageData] = snakeCaseFormat(Json.format)
  implicit val fileDataFormat: OFormat[FileData] = new OFormat[FileData] {
    private val simpleFormat = snakeCaseFormat(Json.format[FileData])

    override def reads(json: JsValue): JsResult[FileData] = {
      val complexParse = for {
        jsObj <- json.asOpt[JsObject]
        imageData <- jsObj.asOpt[ImageData]
        mutatedObj = jsObj ++ JsObject(Map("imageData" -> Json.toJsObject(imageData)))
      } yield simpleFormat.reads(mutatedObj)
      complexParse.getOrElse(simpleFormat.reads(json))
    }
    override def writes(o: FileData): JsObject = {
      val base = simpleFormat.writes(o)
      val mutated = for {
        imageDataVal <- base.value.get("imageData")
        imageDataObj <- imageDataVal.asOpt[JsObject]
      } yield (base - "imageData") ++ imageDataObj
      mutated.getOrElse(base)
    }
  }

  final case class Note(title: String, body: String) extends PushData
  final case class Link(title: String, body: String, url: String) extends PushData
  final case class File(body: String, fileData: FileData) extends PushData

  implicit val format: OFormat[PushData] = formatFor(
    "note" -> Json.format[Note],
    "link" -> Json.format[Link],
    "file" -> new OFormat[File] {
      private val baseFormat = Json.format[File]
      override def reads(json: JsValue): JsResult[File] = {
        for {
          jsObj <- json.validate[JsObject]
          fileData <- jsObj.validate[FileData]
          mutatedObj = jsObj ++ JsObject(Map("fileData" -> Json.toJsObject(fileData)))
          result <- baseFormat.reads(mutatedObj)
        } yield result
      }
      override def writes(o: File): JsObject = {
        val fileDataObj = Json.toJsObject(o.fileData)
        baseFormat.writes(o) - "fileData" ++ fileDataObj
      }
    }
  )
}

sealed trait PushDirection
object PushDirection {
  case object Self extends PushDirection
  case object Outgoing extends PushDirection
  case object Incoming extends PushDirection

  implicit val format: OFormat[PushDirection] = formatForWithName("direction",
    "self" -> Self,
    "outgoing" -> Outgoing,
    "incoming" -> Incoming
  )
}

final case class PartyInfo(iden: SingleItem, email: String, emailNormalized: String, name: String)
object PartyInfo {
  implicit val format: OFormat[PartyInfo] = snakeCaseFormat(Json.format)
}

final case class InactivePush(baseInfo: DomainObjectBaseInfo) extends Push with InactiveDomainObject
final case class ActivePush(baseInfo: DomainObjectBaseInfo, pushData: PushData, dismissed: Boolean, guid: String,
                            direction: PushDirection, senderInfo: PartyInfo, receiverInfo: PartyInfo,
                            targetDeviceIden: Option[SingleItem], sourceDeviceIden: Option[SingleItem],
                            clientIden: Option[SingleItem], channelIden: Option[SingleItem])
  extends Push with ActiveDomainObject

final case class PushListResponse(devices: Seq[Push], cursor: Option[String]) extends ListResponse[Push] {
  override val results: Seq[Push] = devices
}
final case class PushListRequest(active: Option[Boolean], cursor: Option[String] = None,
                                 modifiedAfter: Option[Instant]= None) extends ListRequest[Push, PushListResponse] {
  override val responseReads: Reads[PushListResponse] = Json.reads[PushListResponse]
  override val objName: String = "pushes"
  override def params: Map[String, String] = super.params ++ Seq(active.map("active" -> _.toString)).flatten
  override def toJson: JsObject = Json.writes[PushListRequest].writes(this)
}
object PushListRequest {
  implicit val format: OFormat[PushListRequest] = Json.format
}

sealed trait PushTarget extends Product with Serializable
object PushTarget {
  final case class Device(deviceIden: SingleItem) extends PushTarget
  final case class Email(Email: String) extends PushTarget
  final case class Channel(channelTag: String) extends PushTarget
  final case class Client(clientIden: SingleItem) extends PushTarget

  implicit val format: OFormat[PushTarget] = new OFormat[PushTarget] {
    private val emailFormat: OFormat[Email] = snakeCaseFormat(Json.format[Email])
    private val channelFormat: OFormat[Channel] = snakeCaseFormat(Json.format[Channel])
    private val clientFormat: OFormat[Client] = snakeCaseFormat(Json.format[Client])
    private val deviceFormat: OFormat[Device] = snakeCaseFormat(Json.format[Device])

    @SuppressWarnings(Array("org.wartremover.warts.Product", "org.wartremover.warts.Serializable"))
    override def reads(json: JsValue): JsResult[PushTarget] =
      NonEmptyList.of(emailFormat, channelFormat, clientFormat, deviceFormat)
        .map(_.reads(json))
        .reduceLeft(_ orElse _)
        .fold(
          JsError.apply,
          (target: PushTarget) => JsSuccess(target)
        )

    override def writes(o: PushTarget): JsObject = o match {
      case v: Device => deviceFormat.writes(v)
      case v: Email => emailFormat.writes(v)
      case v: Channel => channelFormat.writes(v)
      case v: Client => clientFormat.writes(v)
    }
  }
}

final case class CreatePushResponse(push: Push) extends CreateResponse[Push] {
  override val result: Push = push
}
final case class CreatePushRequest(pushTarget: PushTarget, pushData: PushData) extends CreateRequest[Push, CreatePushResponse] {
  override val responseReads: Reads[CreatePushResponse] = (json: JsValue) => Push.format.reads(json).map(CreatePushResponse)
  override val objName: String = "pushes"
  override def toJson: JsObject = Json.writes[CreatePushRequest].writes(this)
}
object CreatePushRequest {
  implicit val format: OFormat[CreatePushRequest] = new OFormat[CreatePushRequest] {
    override def reads(json: JsValue): JsResult[CreatePushRequest] = for {
      pushData <- json.validate[PushData]
      pushTarget <- json.validate[PushTarget]
    } yield CreatePushRequest(pushTarget, pushData)

    override def writes(o: CreatePushRequest): JsObject = Json.toJsObject(o.pushData) ++ Json.toJsObject(o.pushTarget)
  }
}

final case class DeletePushRequest(iden: Iden) extends DeleteRequest[Push] {
  override val objName: String = "pushes"
  override lazy val toJson: JsObject = Json.writes[DeletePushRequest].writes(this)
}
object DeletePushRequest {
  implicit val format: OFormat[DeletePushRequest] = Json.format
}

final case class UpdatePushResponse(push: Push) extends UpdateResponse[Push] {
  override val result: Push = push
}
final case class UpdatePushRequest(iden: SingleItem, dismissed: Boolean) extends UpdateRequest[Push, UpdatePushResponse] {
  override val responseReads: Reads[UpdatePushResponse] = (json: JsValue) => Push.format.reads(json).map(UpdatePushResponse)
  override val objName: String = "pushes"
  override lazy val toJson: JsObject = snakeCaseFormat(Json.format[UpdatePushRequest]).writes(this)
}
object UpdatePushRequest {
  implicit val format: OFormat[UpdatePushRequest] = Json.format
}