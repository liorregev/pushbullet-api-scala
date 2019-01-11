package com.github.liorregev.pushbullet.listener

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream._
import akka.stream.scaladsl._
import cats.syntax.either._
import ch.qos.logback.classic.LoggerContext
import com.github.liorregev.pushbullet
import com.github.liorregev.pushbullet.domain.{Device, Push}
import com.github.liorregev.pushbullet.serialization._
import play.api.libs.json.{JsResult, _}

import scala.concurrent.ExecutionContext


sealed trait ProtoMessage extends Product with Serializable

object ProtoMessage {
  implicit val format: OFormat[ProtoMessage] = formatFor(
    "nop" -> Nop,
    "push" -> Json.format[NewPush],
    "tickle" -> Json.format[Tickle]
  )
}
case object Nop  extends ProtoMessage
final case class NewPush(push: Push) extends ProtoMessage
sealed trait TickleSubtype extends Product with Serializable
object TickleSubtype {
  case object Push extends TickleSubtype
  case object Device extends TickleSubtype

  implicit val format: Format[TickleSubtype] = new Format[TickleSubtype] {
    override def reads(json: JsValue): JsResult[TickleSubtype] = for {
      subtype <- json.validate[String]
      result <- subtype match {
        case "push" => JsSuccess(Push)
        case "device" => JsSuccess(Device)
        case _ => JsError("Unknown subtype")
      }
    } yield result

    override def writes(o: TickleSubtype): JsValue = o match {
      case Push => JsString("push")
      case Device => JsString("device")
    }
  }
}
final case class Tickle(subtype: TickleSubtype) extends ProtoMessage

trait Handler extends PushHandler with DeviceHandler

trait NopHandler extends Handler {
  override def onPush(push: Push): Unit = {}
  override def onDevice(device: Device): Unit = {}
}

sealed trait ListenerError extends Product with Serializable
final case class ParseError(jsError: JsError) extends ListenerError
case object InvalidMessageType extends ListenerError

class Listener(client: pushbullet.Client, handler: Handler)
              (implicit loggerFactory: LoggerContext, ec: ExecutionContext) {
  val processGraph: Graph[FlowShape[Message, ProtoMessage], NotUsed] = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val parse = builder.add(new MapStage[Message, Either[ListenerError, ProtoMessage]]({
      case text: TextMessage.Strict => Json.parse(text.text).validate[ProtoMessage].asEither.leftMap(errors => ParseError(JsError(errors)))
      case _ => InvalidMessageType.asLeft[ProtoMessage]
    }))
    val split = builder.add(new SplitJunction())
    val nopProcessor = builder.add(new NopProcessor())
    val tickleProcessor = builder.add(new TickleProcessor(client, handler, handler))
    val merge = builder.add(Merge[ProtoMessage](3))

    parse.out ~> split.in
    split.out0 ~> nopProcessor ~> merge
    split.out1 ~> merge
    split.out2 ~> tickleProcessor ~> merge

    FlowShape(parse.in, merge.out)
  }
}