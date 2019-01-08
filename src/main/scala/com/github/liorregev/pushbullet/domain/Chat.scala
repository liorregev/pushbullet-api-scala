package com.github.liorregev.pushbullet.domain

import java.time.Instant

import cats.syntax.either._
import com.github.liorregev.pushbullet.serialization._
import play.api.libs.json._

sealed trait Partner extends Product with Serializable

object Partner {
  final case class ByEmail(email: String, emailNormalized: String, imageUrl: String) extends Partner
  final case class ByUser(iden: Iden, email: String, emailNormalized: String, imageUrl: String, name: String) extends Partner

  implicit val format: OFormat[Partner] =
    formatFor(
      "email" -> snakeCaseFormat(Json.format[ByEmail]),
      "user" -> snakeCaseFormat(Json.format[ByUser])
    )
}

sealed trait Chat extends DomainObject {
  override final val name: String = "chats"
  def iden: Iden
  def created: Instant
  def modified: Instant
  def isActive: Boolean
}
final case class ActiveChat(iden: Iden, created: Instant, modified: Instant, muted: Option[Boolean], `with`: Partner) extends Chat {
  override def isActive: Boolean = true
}
final case class InactiveChat(iden: Iden, created: Instant, modified: Instant) extends Chat {
  override def isActive: Boolean = false
}

object Chat {
  implicit val format: OFormat[Chat] = new OFormat[Chat] {
    private val activeChatWrites = Json.writes[ActiveChat]
    private val activeChatReads = Json.reads[ActiveChat]
    private val inactiveChatWrites = Json.writes[InactiveChat]
    private val inactiveChatReads = Json.reads[InactiveChat]

    override def reads(json: JsValue): JsResult[Chat] = {
      val result = for {
        obj <- json match {
          case o: JsObject => o.asRight[JsError]
          case _ => JsError("Not an object").asLeft[JsObject]
        }
        activeField <- Either.fromOption(obj.value.get("active"), JsError(__ \ "active", "Missing"))
        active <- activeField.validate[Boolean].asEither.leftMap(errors => JsError(errors))
      } yield if(active) activeChatReads.reads(obj) else inactiveChatReads.reads(obj)
      result.fold(identity, identity)
    }

    override def writes(o: Chat): JsObject = o match {
      case activeChat: ActiveChat => activeChatWrites.writes(activeChat)
      case inactiveChat: InactiveChat => inactiveChatWrites.writes(inactiveChat)
    }
  }
}

final case class ChatListResponse(chats: Seq[Chat], cursor: Option[String]) extends Response[Chat]
final case class ChatListRequest(cursor: Option[String] = None) extends Request[Chat, ChatListResponse] {
  override val op: Operation = Operations.List
  override val responseReads: Reads[ChatListResponse] = Json.reads[ChatListResponse]
  override val objName: String = "chats"
}
object ChatListRequest {
  implicit val format: OFormat[ChatListRequest] = Json.format[ChatListRequest]
}

final case class CreateChatResponse(chat: Chat) extends Response[Chat]
final case class CreateChatRequest(cursor: Option[String] = None) extends Request[Chat, CreateChatResponse] {
  override val op: Operation = Operations.Create
  override val responseReads: Reads[CreateChatResponse] = (json: JsValue) => Chat.format.reads(json).map(CreateChatResponse)
  override val objName: String = "chats"
}
object CreateChatRequest {
  implicit val format: OFormat[CreateChatRequest] = Json.format[CreateChatRequest]
}

case object DeleteChatResponse extends Response[Chat]
final case class DeleteChatRequest(iden: Iden) extends Request[Chat, DeleteChatResponse.type] {
  override val op: Operation = Operations.Delete(iden)
  override val responseReads: Reads[DeleteChatResponse.type] = _ => JsSuccess(DeleteChatResponse)
  override val objName: String = "chats"
}
object DeleteChatRequest {
  implicit val format: OFormat[DeleteChatRequest] = Json.format[DeleteChatRequest]
}

final case class UpdateChatResponse(chat: Chat) extends Response[Chat]
final case class UpdateChatRequest(iden: Iden, muted: Boolean) extends Request[Chat, UpdateChatResponse] {
  override val op: Operation = Operations.Update(iden)
  override val responseReads: Reads[UpdateChatResponse] = (json: JsValue) => Chat.format.reads(json).map(UpdateChatResponse)
  override val objName: String = "chats"
}
object UpdateChatRequest {
  implicit val format: OFormat[UpdateChatRequest] = Json.format[UpdateChatRequest]
}