package com.github.liorregev.pushbullet

import java.time.Instant
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.ContextInitializer
import com.github.liorregev.pushbullet.domain.{ActivePush, CreatePushRequest, InactivePush, Push, PushData, PushTarget, UpdatePushRequest}
import com.github.liorregev.pushbullet.listener.{DeadConnection, Listener, NopHandler}
import org.scalatest.{EitherValues, FunSuite, Matchers}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ListenerTest extends FunSuite with Matchers with EitherValues {
  implicit lazy val loggerFactory: LoggerContext = {
    val loggerContext = new LoggerContext()
    val contextInitializer = new ContextInitializer(loggerContext)
    contextInitializer.autoConfig()
    loggerContext
  }
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  test("base") {
    val client = new Client("o.i8br1Q7KYE3IXHEPY01i7b1Qz2Z3Mz6j")
    val (stop, closed) = client.startListening(new NopHandler {
      override def onPush(push: Push): Unit = push match {
        case active: ActivePush =>
          if(active.baseInfo.created equals active.baseInfo.modified) {
            println(s"New push with body: ${active.pushData.body} and modified at ${active.baseInfo.modified}")
            client.request(UpdatePushRequest(active.baseInfo.iden, dismissed = true))
          } else {
            println(s"Push with iden ${push.baseInfo.iden} is now dismissed")
          }
        case inactive: InactivePush =>
          println(s"Push with iden ${inactive.baseInfo.iden} deleted")
      }
    })
    try {
      Await.result(closed, 5 seconds)
    } catch {
      case _: TimeoutException =>
        client.request(CreatePushRequest(PushTarget.Broadcast, PushData.Note("title", "body")))
    }
    try {
      Await.result(closed, 5 seconds)
    } catch {
      case _: TimeoutException =>
        stop()
        Await.result(closed, 15 seconds)
    }
  }

  test("No messages") {
    class TestClient() extends Client("") {
      override def loadLatestServerTime()(implicit ec: ExecutionContext): Future[Instant] =
        Future.successful(Instant.now())
    }

    val listener = new Listener(new TestClient(), new NopHandler {})
    val flow = Flow.fromGraph(listener.processGraph)
    val (_, done) = Source.maybe.viaMat(flow)(Keep.left)
      .toMat(Sink.last)(Keep.both)
      .run()
    val result = Await.result(done, 2 minutes)
    result.left.value should be (DeadConnection)
  }
}
