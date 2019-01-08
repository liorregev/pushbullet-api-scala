package com.github.liorregev.pushbullet.serialization

import play.api.libs.json._

final case class SerializableADTObject[T](typeName: String, instance: T) extends SerializableADT[T] {
  val reads: PartialFunction[JsValue, JsResult[T]] = {
    case obj: JsObject if (obj \ "type").as[String] == typeName =>
      JsSuccess(instance)
  }

  val writes: PartialFunction[T, JsObject] = {
    case obj if obj == instance =>
      JsObject(Map("type" -> JsString(typeName)))
  }
}
