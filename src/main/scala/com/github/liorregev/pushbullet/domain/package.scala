package com.github.liorregev.pushbullet

import java.time.Instant

import play.api.libs.json.{JsError, JsNumber, JsSuccess, Reads}

package object domain {
  type Iden = String

  implicit val instantReads: Reads[Instant] = {
    case JsNumber(value) => JsSuccess(Instant.ofEpochMilli(value.toLong * 1000))
    case _ => JsError("Incompatible type")
  }
}
