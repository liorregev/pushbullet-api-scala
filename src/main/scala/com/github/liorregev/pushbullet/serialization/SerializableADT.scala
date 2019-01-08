package com.github.liorregev.pushbullet.serialization

import play.api.libs.json._

trait SerializableADT[T] {
  def reads: PartialFunction[JsValue, JsResult[T]]
  def writes: PartialFunction[T, JsObject]
}