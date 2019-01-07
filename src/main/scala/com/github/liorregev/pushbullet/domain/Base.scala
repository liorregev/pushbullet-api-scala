package com.github.liorregev.pushbullet.domain

import cats.Id
import com.softwaremill.sttp.Method
import play.api.libs.json._

trait DomainObject {
  def name: String
}

sealed trait Operation[C[_]] {
  def method: Method
}
object Operations {
  case object Create extends Operation[Id] {
    override val method: Method = Method.POST
  }
  case object Update extends Operation[Id] {
    override val method: Method = Method.POST
  }
  case object Delete extends Operation[Id] {
    override val method: Method = Method.DELETE
  }
  case object List extends Operation[Seq] {
    override val method: Method = Method.GET
  }
}

trait Response[Obj <: DomainObject, C[_]] extends Product with Serializable {
  def result: C[Obj]
}

trait Request[Obj <: DomainObject, C[_], Resp <: Response[Obj, C]] extends Product with Serializable {
  def op: Operation[C]
  def responseReads: Reads[Resp]
}

