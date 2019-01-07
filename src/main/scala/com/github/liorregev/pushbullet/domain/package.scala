package com.github.liorregev.pushbullet

import play.api.libs.json._

package object domain {
  implicit def requestWrites[T <: Request[_, _, _]]: OWrites[T] = Json.writes[T]
}
