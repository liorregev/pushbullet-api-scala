package com.github.liorregev.pushbullet

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.ContextInitializer
import com.github.liorregev.pushbullet.domain.{ActivePush, CreatePushRequest, InactivePush, Push, PushData, PushTarget, UpdatePushRequest}
import com.github.liorregev.pushbullet.listener.NopHandler
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class ListenerTest extends FunSuite with Matchers {
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
}
