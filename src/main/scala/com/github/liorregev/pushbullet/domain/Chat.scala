package com.github.liorregev.pushbullet.domain

import java.time.Instant

import com.github.liorregev.pushbullet.serialization._
import play.api.libs.json._

sealed trait Partner extends Product with Serializable {
  def email: String
  def emailNormalized: String
  def imageUrl: String
}

object Partner {
  final case class ByEmail(email: String, emailNormalized: String, imageUrl: String) extends Partner
  final case class ByUser(iden: SingleItem, email: String, emailNormalized: String, imageUrl: String, name: String) extends Partner

  implicit val format: OFormat[Partner] =
    formatFor(
      "email" -> snakeCaseFormat(Json.format[ByEmail]),
      "user" -> snakeCaseFormat(Json.format[ByUser])
    )
}

sealed trait Chat extends DomainObject {
  override final val name: String = "chats"
}
final case class ActiveChat(baseInfo: DomainObjectBaseInfo, muted: Option[Boolean], `with`: Partner) extends Chat with ActiveDomainObject
final case class InactiveChat(baseInfo: DomainObjectBaseInfo) extends Chat with InactiveDomainObject

object Chat {
  implicit val format: OFormat[Chat] = domainObjectFormat(Json.format[ActiveChat], Json.format[InactiveChat])
}

final case class ChatListResponse(chats: Seq[Chat], cursor: Option[String]) extends ListResponse[Chat] {
  override val results: Seq[Chat] = chats
}
final case class ChatListRequest(cursor: Option[String] = None, modifiedAfter: Option[Instant]= None) extends ListRequest[Chat, ChatListResponse] {
  override val responseReads: Reads[ChatListResponse] = Json.reads[ChatListResponse]
  override val objName: String = "chats"
  override def toJson: JsObject = Json.writes[ChatListRequest].writes(this)
}
object ChatListRequest {
  implicit val format: OFormat[ChatListRequest] = Json.format
}

final case class CreateChatResponse(chat: Chat) extends CreateResponse[Chat] {
  override val result: Chat = chat
}
final case class CreateChatRequest(email: String) extends CreateRequest[Chat, CreateChatResponse] {
  override val responseReads: Reads[CreateChatResponse] = (json: JsValue) => Chat.format.reads(json).map(CreateChatResponse)
  override val objName: String = "chats"
  override def toJson: JsObject = Json.writes[CreateChatRequest].writes(this)
}
object CreateChatRequest {
  implicit val format: OFormat[CreateChatRequest] = Json.format
}

final case class DeleteChatRequest(iden: SingleItem) extends DeleteRequest[Chat] {
  override val objName: String = "chats"
  override lazy val toJson: JsObject = Json.writes[DeleteChatRequest].writes(this)
}
object DeleteChatRequest {
  implicit val format: OFormat[DeleteChatRequest] = Json.format
}

final case class UpdateChatResponse(chat: Chat) extends UpdateResponse[Chat] {
  override val result: Chat = chat
}
final case class UpdateChatRequest(iden: SingleItem, muted: Boolean) extends UpdateRequest[Chat, UpdateChatResponse] {
  override val responseReads: Reads[UpdateChatResponse] = (json: JsValue) => Chat.format.reads(json).map(UpdateChatResponse)
  override val objName: String = "chats"
  override lazy val toJson: JsObject = Json.writes[UpdateChatRequest].writes(this)
}
object UpdateChatRequest {
  implicit val format: OFormat[UpdateChatRequest] = Json.format
}