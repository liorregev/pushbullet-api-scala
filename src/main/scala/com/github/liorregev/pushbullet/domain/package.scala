package com.github.liorregev.pushbullet

import java.time.Instant

import play.api.libs.json._
import cats.syntax.either._

package object domain {
  type Iden = String

  implicit val instantReads: Reads[Instant] = {
    case JsNumber(value) => JsSuccess(Instant.ofEpochMilli(value.toLong * 1000))
    case _ => JsError("Incompatible type")
  }

  def domainObjectFormat[Obj <: DomainObject, A <: Obj with ActiveDomainObject, I <: Obj with InactiveDomainObject](activeFormat: OFormat[A], inactiveFormat: OFormat[I]): OFormat[Obj] = new OFormat[Obj] {
    override def reads(json: JsValue): JsResult[Obj] = {
      val result = for {
        obj <- json match {
          case o: JsObject => o.asRight[JsError]
          case _ => JsError("Not an object").asLeft[JsObject]
        }
        activeField <- Either.fromOption(obj.value.get("active"), JsError(__ \ "active", "Missing"))
        active <- activeField.validate[Boolean].asEither.leftMap(errors => JsError(errors))
      } yield if(active) activeFormat.reads(obj).map((o: Obj) => o) else inactiveFormat.reads(obj).map((o: Obj) => o)
      result.fold(identity, identity)
    }

    override def writes(o: Obj): JsObject = o match {
      case active: ActiveDomainObject => activeFormat.writes(active.asInstanceOf[A])
      case inactive: InactiveDomainObject => inactiveFormat.writes(inactive.asInstanceOf[I])
    }
  }
}
