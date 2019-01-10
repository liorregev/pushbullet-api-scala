package com.github.liorregev.pushbullet.domain

import play.api.libs.json._

final case class User(baseInfo: DomainObjectBaseInfo, email: String, emailNormalized: String, name: String,
                      imageUrl: String, maxUploadSize: Long, referredCount: Long, referrerIden: Option[SingleItem])
  extends DomainObject {
  override val isActive: Boolean = true
}

object User {
  implicit val format: OFormat[User] = new OFormat[User] {
    private val simpleFormat = Json.format[User]
    override def reads(json: JsValue): JsResult[User] = for {
      baseInfoObj <- json.validate[DomainObjectBaseInfo].map(info => JsObject(Map("baseInfo" -> Json.toJsObject(info))))
      mutatedObj <- json.validate[JsObject].map(_ ++ baseInfoObj)
      result <- mutatedObj.validate[User](simpleFormat)
    } yield result

    override def writes(o: User): JsObject = simpleFormat.writes(o) - "baseInfo" ++ Json.toJsObject(o.baseInfo)
  }
}

final case class GetUserResponse(user: User) extends Response[User]
case object GetUserRequest extends Request[User, GetUserResponse] {
  override val op: Operation = Operation.Get("me")
  override def responseReads: Reads[GetUserResponse] = (json: JsValue) => User.format.reads(json).map(GetUserResponse)
  override val objName: String = "users"
  override val toJson: JsObject = JsObject(Seq.empty)
}