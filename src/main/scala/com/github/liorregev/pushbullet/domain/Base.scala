package com.github.liorregev.pushbullet.domain

import java.time.Instant

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import play.api.libs.json._

final case class DomainObjectBaseInfo(iden: SingleItem, created: Instant, modified: Instant)
object DomainObjectBaseInfo {
  implicit val format: OFormat[DomainObjectBaseInfo] = Json.format
}

private[pushbullet] trait DomainObject extends Product with Serializable {
  def name: String
  def baseInfo: DomainObjectBaseInfo
  def isActive: Boolean
}

private[domain] trait ActiveDomainObject extends DomainObject { thisObject =>
  override final val isActive: Boolean = true
}

private[domain] trait InactiveDomainObject extends DomainObject { thisObject =>
  override final val isActive: Boolean = false
}

private[pushbullet] sealed trait Operation {
  def method: HttpMethod
}

private[pushbullet] object Operation {
  case object Create extends Operation {
    override val method: HttpMethod = HttpMethods.POST
  }
  final case class Update(iden: SingleItem) extends Operation {
    override val method: HttpMethod = HttpMethods.POST
  }
  final case class Delete(iden: Iden) extends Operation {
    override val method: HttpMethod = HttpMethods.DELETE
  }
  final case class List(params: Map[String, String] = Map.empty) extends Operation {
    override val method: HttpMethod = HttpMethods.GET
  }
  final case class Get(what: String) extends Operation {
    override val method: HttpMethod = HttpMethods.GET
  }
}

private[pushbullet] trait Response[+Obj <: DomainObject] extends Product with Serializable

private[pushbullet] trait Request[Obj <: DomainObject, Resp <: Response[Obj]] extends Product with Serializable {
  def op: Operation
  def responseReads: Reads[Resp]
  def objName: String
  def toJson: JsObject
}

private[domain] trait ListResponse[Obj <: DomainObject] extends Response[Obj] {
  def results: Seq[Obj]
  def cursor: Option[String]
}
private[domain] trait ListRequest[Obj <: DomainObject, Resp <: ListResponse[Obj]] extends Request[Obj, Resp] {
  def cursor: Option[String]
  def modifiedAfter: Option[Instant]
  def params: Map[String, String] = Map.empty
  override final val op: Operation = Operation.List(params ++ Seq(
    cursor.map("cursor" -> _),
    modifiedAfter.map(_.toEpochMilli.toDouble / 1000).map("modified_after" -> _.toString)
  ).flatten)
}

private[domain] trait CreateResponse[Obj <: DomainObject] extends Response[Obj] {
  def result: Obj
}
private[domain] trait CreateRequest[Obj <: DomainObject, Resp <: CreateResponse[Obj]] extends Request[Obj, Resp] {
  override final val op: Operation = Operation.Create
}

case object DeleteResponse extends Response[Nothing]
private[domain] trait DeleteRequest[Obj <: DomainObject] extends Request[Obj, DeleteResponse.type] {
  def iden: Iden
  override final val op: Operation = Operation.Delete(iden)
  override final val responseReads: Reads[DeleteResponse.type] = _ => JsSuccess(DeleteResponse)
}

private[domain] trait UpdateResponse[Obj <: DomainObject] extends Response[Obj] {
  def result: Obj
}
private[domain] trait UpdateRequest[Obj <: DomainObject, Resp <: UpdateResponse[Obj]] extends Request[Obj, Resp] {
  def iden: SingleItem
  override final val op: Operation = Operation.Update(iden)
}