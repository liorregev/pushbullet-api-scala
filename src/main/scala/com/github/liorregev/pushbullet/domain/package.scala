package com.github.liorregev.pushbullet

import java.time.Instant

import play.api.libs.json._

package object domain {

  implicit val instantFormat: Format[Instant] = new Format[Instant] {
    override def reads(json: JsValue): JsResult[Instant] = json match {
      case JsNumber(value) => JsSuccess(Instant.ofEpochMilli(value.toLong * 1000))
      case _ => JsError("Incompatible type")
    }

    override def writes(o: Instant): JsValue = JsNumber(BigDecimal(o.toEpochMilli) / 1000)
  }

  def domainObjectFormat[Obj <: DomainObject, A <: Obj with ActiveDomainObject, I <: Obj with InactiveDomainObject](activeFormat: OFormat[A], inactiveFormat: OFormat[I]): OFormat[Obj] = new OFormat[Obj] {
    override def reads(json: JsValue): JsResult[Obj] = {
      for {
        jsObj <- json.validate[JsObject]
        baseInfo <- json.validate[DomainObjectBaseInfo]
        mutatedObj = jsObj ++ JsObject(Map("baseInfo" -> Json.toJsObject(baseInfo)))
        active <- (mutatedObj \ "active").validate[Boolean]
        result <- if (active) activeFormat.reads(mutatedObj).map((o: Obj) => o) else inactiveFormat.reads(mutatedObj).map((o: Obj) => o)
      } yield result
    }

    override def writes(o: Obj): JsObject = {
      val initialConversion = o match {
        case active: ActiveDomainObject => activeFormat.writes(active.asInstanceOf[A])
        case inactive: InactiveDomainObject => inactiveFormat.writes(inactive.asInstanceOf[I])
      }
      val mutated = for {
        baseInfoValue <- initialConversion.value.get("baseInfo")
        baseInfoObj <- baseInfoValue.validate[JsObject].asOpt
      } yield (initialConversion - "baseInfo") ++ baseInfoObj
      mutated.getOrElse(initialConversion)
    }
  }
}
