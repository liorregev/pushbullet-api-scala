package com.github.liorregev.pushbullet.domain

import play.api.libs.json._

sealed trait Iden

object Iden {
  val ALL = "ALL"

  implicit val format: Format[Iden] = new Format[Iden] {
    override def reads(json: JsValue): JsResult[Iden] =
      json.validate[String].map {
        case ALL => AllItems
        case iden => SingleItem(iden)
      }

    override def writes(o: Iden): JsValue = o match {
      case AllItems => JsString(ALL)
      case single: SingleItem => Json.toJson(single)
    }
  }
}

final case class SingleItem(iden: String) extends Iden

object SingleItem {
  implicit val format: Format[SingleItem] = new Format[SingleItem] {
    override def reads(json: JsValue): JsResult[SingleItem] = json.validate[String].map(SingleItem.apply)
    override def writes(o: SingleItem): JsValue = JsString(o.iden)
  }
}

case object AllItems extends Iden