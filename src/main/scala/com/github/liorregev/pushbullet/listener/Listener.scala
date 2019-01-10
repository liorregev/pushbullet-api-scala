package com.github.liorregev.pushbullet.listener

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream._
import akka.stream.scaladsl._
import cats.syntax.either._
import ch.qos.logback.classic.LoggerContext
import com.github.liorregev.pushbullet.domain.Push
import com.github.liorregev.pushbullet.serialization._
import play.api.libs.json.{JsResult, _}


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

trait Handler {
  def onPush(push: Push): Unit
}

sealed trait ListenerError extends Product with Serializable
final case class ParseError(jsError: JsError) extends ListenerError
case object InvalidMessageType extends ListenerError

class Listener()(implicit system: ActorSystem, loggerFactory: LoggerContext, materializer: ActorMaterializer) {

  val processGraph: Graph[SinkShape[Message], NotUsed] = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val parse = new MapStage[Message, Either[ListenerError, ProtoMessage]]({
      case text: TextMessage.Strict => Json.parse(text.text).validate[ProtoMessage].asEither.leftMap(errors => ParseError(JsError(errors)))
      case _ => InvalidMessageType.asLeft[ProtoMessage]
    })
    val nopSink = new NopSink()

    val split = builder.add(new SplitJunction())
    parse.out ~> split.in
    split.out0 ~> nopSink
    SinkShape.of(parse.in)
  }
}