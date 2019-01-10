package com.github.liorregev.pushbullet.domain

import java.time.Instant

import play.api.libs.json._
import com.github.liorregev.pushbullet.serialization._

sealed trait Subscription extends DomainObject {
  override final val name: String = "subscriptions"
}
object Subscription {
  implicit val format: OFormat[Subscription] =
    domainObjectFormat(
      new OFormat[ActiveSubscription] {
        private val simpleWrites = Json.writes[ActiveSubscription]
        override def reads(json: JsValue): JsResult[ActiveSubscription] = for {
          baseInfo <- json.validate[DomainObjectBaseInfo]
          channel <- json.validate[Channel]
          muted <- (json \ "muted").validate[Boolean]
        } yield ActiveSubscription(baseInfo, channel, muted)

        override def writes(o: ActiveSubscription): JsObject = simpleWrites.writes(o) - "channel" ++ Json.toJsObject(o.channel)
      },
      Json.format[InactiveSubscription]
    )
}

final case class Channel(iden: SingleItem, tag: String, name: String, description: String,
                         imageUrl: String, websiteUrl: String)

object Channel {
  implicit val format: OFormat[Channel] = snakeCaseFormat(Json.format)
}

final case class InactiveSubscription(baseInfo: DomainObjectBaseInfo) extends Subscription with InactiveDomainObject
final case class ActiveSubscription(baseInfo: DomainObjectBaseInfo, channel: Channel, muted: Boolean)
  extends Subscription with ActiveDomainObject

final case class SubscriptionListResponse(subscriptions: Seq[Subscription],
                                          cursor: Option[String]) extends ListResponse[Subscription] {
  override val results: Seq[Subscription] = subscriptions
}
final case class SubscriptionListRequest(cursor: Option[String] = None) extends ListRequest[Subscription, SubscriptionListResponse] {
  override val responseReads: Reads[SubscriptionListResponse] = Json.reads[SubscriptionListResponse]
  override val objName: String = "subscriptions"
  override def toJson: JsObject = Json.writes[SubscriptionListRequest].writes(this)
  override val modifiedAfter: Option[Instant] = None
}
object SubscriptionListRequest {
  implicit val format: OFormat[SubscriptionListRequest] = Json.format
}

final case class CreateSubscriptionResponse(subscription: Subscription) extends CreateResponse[Subscription] {
  override val result: Subscription = subscription
}
final case class CreateSubscriptionRequest(channelTag: String) extends CreateRequest[Subscription, CreateSubscriptionResponse] {
  override val responseReads: Reads[CreateSubscriptionResponse] = (json: JsValue) => Subscription.format.reads(json).map(CreateSubscriptionResponse)
  override val objName: String = "subscriptions"
  override def toJson: JsObject = Json.writes[CreateSubscriptionRequest].writes(this)
}
object CreateSubscriptionRequest {
  implicit val format: OFormat[CreateSubscriptionRequest] = snakeCaseFormat(Json.format)
}

final case class DeleteSubscriptionRequest(iden: SingleItem) extends DeleteRequest[Subscription] {
  override val objName: String = "subscriptions"
  override lazy val toJson: JsObject = Json.writes[DeleteSubscriptionRequest].writes(this)
}
object DeleteSubscriptionRequest {
  implicit val format: OFormat[DeleteSubscriptionRequest] = Json.format
}

final case class UpdateSubscriptionResponse(subscription: Subscription) extends UpdateResponse[Subscription] {
  override val result: Subscription = subscription
}
final case class UpdateSubscriptionRequest(iden: SingleItem, muted: Boolean) extends UpdateRequest[Subscription, UpdateSubscriptionResponse] {
  override val responseReads: Reads[UpdateSubscriptionResponse] = (json: JsValue) => Subscription.format.reads(json).map(UpdateSubscriptionResponse)
  override val objName: String = "subscriptions"
  override lazy val toJson: JsObject = Json.writes[UpdateSubscriptionRequest].writes(this)
}
object UpdateSubscriptionRequest {
  implicit val format: OFormat[UpdateSubscriptionRequest] = Json.format
}