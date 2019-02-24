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

import scala.concurrent.{ExecutionContext, TimeoutException}
import scala.concurrent.duration._


sealed trait ProtoMessage extends Product with Serializable

object ProtoMessage {
  implicit val format: OFormat[ProtoMessage] = formatFor(
    "nop" -> Nop,
    "reconnect" -> Reconnect,
    "push" -> Json.format[NewPush],
    "tickle" -> Json.format[Tickle]
  )
}
case object Nop  extends ProtoMessage
case object Reconnect  extends ProtoMessage
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

trait Handler extends PushHandler with DeviceHandler with EphemeralPushHandler with ErrorHandler

trait NopHandler extends Handler {
  override def onEphemeralPush(push: Push): Unit = {}
  override def onPush(push: Push): Unit = {}
  override def onDevice(device: Device): Unit = {}
  override def onError(error: ListenerError): Unit = {}
}

sealed trait ListenerError extends Product with Serializable
final case class ParseError(jsError: JsError) extends ListenerError
case object InvalidMessageType extends ListenerError
case object DeadConnection extends ListenerError

case object ReconnectError extends Exception

class Listener(client: pushbullet.Client, handler: Handler)
              (implicit loggerFactory: LoggerContext, ec: ExecutionContext) {
  type CombinedMessage = Either[ListenerError, ProtoMessage]

  val processGraph: Graph[FlowShape[Message, CombinedMessage], NotUsed] =
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val parse = builder.add(new MapStage[Message, CombinedMessage]({
        case text: TextMessage.Strict => Json.parse(text.text).validate[ProtoMessage].asEither.leftMap(errors => ParseError(JsError(errors)))
        case _ => InvalidMessageType.asLeft[ProtoMessage]
      }))
      val split = builder.add(new SplitJunction())
      val reconnectProcessor = builder.add(new ReconnectProcessor())
      val nopProcessor = builder.add(new NopProcessor())
      val tickleProcessor = builder.add(new TickleProcessor(client, handler, handler))
      val ephemeralPushProcessor = builder.add(new NewPushProcessor(handler))
      val errorProcessor = builder.add(new ErrorProcessor(handler))
      val mergeProtos = builder.add(Merge[ProtoMessage](4))
      val merge = builder.add(MergePreferred[CombinedMessage](1))
      val protoToEither = builder.add(new MapStage[ProtoMessage, CombinedMessage](_.asRight[ListenerError]))
      val errorToEither = builder.add(new MapStage[ListenerError, CombinedMessage](_.asLeft[ProtoMessage]))
      val timedStopper = builder.add(
        Flow[CombinedMessage]
          .idleTimeout(1 minute)
          .recoverWithRetries(1, {
            case _: TimeoutException => Source.single(DeadConnection.asLeft[ProtoMessage])
          }))

      parse.out ~> split.in
      split.out0 ~> nopProcessor ~> mergeProtos
      split.out1 ~> ephemeralPushProcessor ~> mergeProtos
      split.out2 ~> tickleProcessor ~> mergeProtos
      split.out4 ~> reconnectProcessor ~> mergeProtos
      split.out3 ~> errorProcessor ~> errorToEither ~> merge.preferred
      mergeProtos.out.takeWhile(msg => msg match {
        case Reconnect => false
        case _ => true
      }, inclusive = true) ~> protoToEither ~> merge ~> timedStopper

      FlowShape(parse.in, timedStopper.out)
    }
}