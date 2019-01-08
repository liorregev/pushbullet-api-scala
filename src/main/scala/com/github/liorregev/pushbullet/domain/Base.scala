package com.github.liorregev.pushbullet.domain

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import play.api.libs.json._

trait DomainObject extends Product with Serializable {
  def name: String
}

sealed trait Operation {
  def method: HttpMethod
}

object Operations {
  case object Create extends Operation {
    override val method: HttpMethod = HttpMethods.POST
  }
  final case class Update(iden: Iden) extends Operation {
    override val method: HttpMethod = HttpMethods.POST
  }
  final case class Delete(iden: Iden) extends Operation {
    override val method: HttpMethod = HttpMethods.DELETE
  }
  case object List extends Operation {
    override val method: HttpMethod = HttpMethods.GET
  }
}

trait Response[Obj <: DomainObject] extends Product with Serializable

trait Request[Obj <: DomainObject, Resp <: Response[Obj]] extends Product with Serializable {
  def op: Operation
  def responseReads: Reads[Resp]
  def objName: String
  def toJson: JsObject
}
