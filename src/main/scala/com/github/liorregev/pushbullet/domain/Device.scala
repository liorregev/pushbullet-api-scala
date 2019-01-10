package com.github.liorregev.pushbullet.domain

import java.time.Instant

import play.api.libs.json._

import com.github.liorregev.pushbullet.serialization._

sealed trait Device extends DomainObject {
  override final val name: String = "devices"
}

object Device {
  implicit val format: OFormat[Device] = domainObjectFormat(Json.format[ActiveDevice], Json.format[InactiveDevice])
}

final case class InactiveDevice(baseInfo: DomainObjectBaseInfo) extends Device with InactiveDomainObject
final case class ActiveDevice(baseInfo: DomainObjectBaseInfo, icon: String, nickname: String,
                              generatedNickname: Boolean, manufacturer: String, model: String, appVersion: Long,
                              fingerprint: String, key_fingerprint: String, pushToken: String, hasSms: String)
  extends Device with ActiveDomainObject

final case class DeviceListResponse(devices: Seq[Device], cursor: Option[String]) extends ListResponse[Device] {
  override val results: Seq[Device] = devices
}
final case class DeviceListRequest(cursor: Option[String] = None, modifiedAfter: Option[Instant]= None) extends ListRequest[Device, DeviceListResponse] {
  override val responseReads: Reads[DeviceListResponse] = Json.reads[DeviceListResponse]
  override val objName: String = "chats"
  override def toJson: JsObject = Json.writes[DeviceListRequest].writes(this)
}
object DeviceListRequest {
  implicit val format: OFormat[DeviceListRequest] = Json.format
}

final case class CreateDeviceResponse(device: Device) extends CreateResponse[Device] {
  override val result: Device = device
}
final case class CreateDeviceRequest(email: String) extends CreateRequest[Device, CreateDeviceResponse] {
  override val responseReads: Reads[CreateDeviceResponse] = (json: JsValue) => Device.format.reads(json).map(CreateDeviceResponse)
  override val objName: String = "chats"
  override def toJson: JsObject = Json.writes[CreateDeviceRequest].writes(this)
}
object CreateDeviceRequest {
  implicit val format: OFormat[CreateDeviceRequest] = Json.format
}

final case class DeleteDeviceRequest(iden: SingleItem) extends DeleteRequest[Device] {
  override val objName: String = "chats"
  override lazy val toJson: JsObject = Json.writes[DeleteDeviceRequest].writes(this)
}
object DeleteDeviceRequest {
  implicit val format: OFormat[DeleteDeviceRequest] = Json.format
}

final case class UpdateDeviceResponse(device: Device) extends UpdateResponse[Device] {
  override val result: Device = device
}
final case class UpdateDeviceRequest(iden: SingleItem, nickname: String, model: String, manufacturer: String, pushToken: String, appVersion: Long, icon: String, hasSms: String) extends UpdateRequest[Device, UpdateDeviceResponse] {
  override val responseReads: Reads[UpdateDeviceResponse] = (json: JsValue) => Device.format.reads(json).map(UpdateDeviceResponse)
  override val objName: String = "chats"
  override lazy val toJson: JsObject = snakeCaseFormat(Json.format[UpdateDeviceRequest]).writes(this)
}
object UpdateDeviceRequest {
  implicit val format: OFormat[UpdateDeviceRequest] = Json.format
}