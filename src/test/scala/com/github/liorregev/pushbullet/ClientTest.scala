package com.github.liorregev.pushbullet

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.ContextInitializer
import com.github.liorregev.pushbullet.domain._
import com.softwaremill.sttp.asynchttpclient.future.AsyncHttpClientFutureBackend
import com.softwaremill.sttp.{SttpBackend, UriContext}
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ClientTest extends FunSuite with Matchers {
  implicit lazy val loggerFactory: LoggerContext = {
    val loggerContext = new LoggerContext()
    val contextInitializer = new ContextInitializer(loggerContext)
    contextInitializer.autoConfig()
    loggerContext
  }

  test("base") {
    implicit val backend: SttpBackend[Future, Nothing] = AsyncHttpClientFutureBackend()
    val client = new Client(uri"https://api.pushbullet.com/v2", "o.i8br1Q7KYE3IXHEPY01i7b1Qz2Z3Mz6j")
    val response = client.request(ChatListRequest())
    val result = Await.result(response, 10 seconds)
    result match {
      case Right(resp) =>
        val deletes = resp.chats.filter(_.isActive).map(chat => UpdateChatRequest(chat.iden, muted = true)).map(client.request)
        val deleteResult = Await.result(Future.sequence(deletes), 10 seconds)
        println(deleteResult)
      case _ =>
    }
  }
}
