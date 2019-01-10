package com.github.liorregev.pushbullet

import java.io.File
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.ContextInitializer
import com.github.liorregev.pushbullet.domain._
import org.scalatest._

import scala.concurrent.Future

@SuppressWarnings(Array("org.wartremover.warts.Product", "org.wartremover.warts.Serializable"))
class ClientTest extends AsyncFeatureSpec with GivenWhenThen with Matchers with EitherValues with OptionValues {
  implicit lazy val loggerFactory: LoggerContext = {
    val loggerContext = new LoggerContext()
    val contextInitializer = new ContextInitializer(loggerContext)
    contextInitializer.autoConfig()
    loggerContext
  }
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  feature("Chats api") {
    val testEmail = "someEmail@someDomain.com"
    val client = new Client("https://api.pushbullet.com/v2", "o.i8br1Q7KYE3IXHEPY01i7b1Qz2Z3Mz6j")
    val testStart = client.request(ChatListRequest())
      .map(_.right.value)
      .map(_.chats.collect {
        case ActiveChat(baseInfo, _, partner) if partner.email == testEmail => baseInfo.iden
      })
      .map(_.map(iden => client.request(DeleteChatRequest(iden)).map(_ => ())))
      .flatMap(responses => Future.sequence(responses).map(_ => Instant.now()))

    scenario("Listing chats") {
      Given("Empty chat list since test started")
      When("Requesting to list chats")
      val response = testStart.flatMap(start => client.request(ChatListRequest(modifiedAfter = Option(start))))
      Then("The response should be empty")
      response.map(result => result.right.value should be (ChatListResponse(Seq.empty, None)))
    }

    scenario("Creating a new chat") {
      Given("Empty chat list since test started")
      When("Creating a new chat")
      val response = client.request(CreateChatRequest(testEmail))
      Then("The chat should be created")
      response.map(_ should be ('right))
    }

    scenario("Creating a duplicate chat") {
      Given("One chat existing")
      val listResponse = testStart.flatMap(start => client.request(ChatListRequest(modifiedAfter = Option(start))))
      When("Creating a duplicate chat")
      val response = listResponse
        .map(_.right.value)
        .flatMap(
          _.chats.headOption.collect {
            case ActiveChat(_, _, partner) => client.request(CreateChatRequest(partner.email))
          }.value
        )
      Then("The chat should be created")
      response.map(result => result should be ('left))
    }

    scenario("Muting chat") {
      Given("One chat existing")
      val listResponse = testStart.flatMap(start => client.request(ChatListRequest(modifiedAfter = Option(start))))
      When("Creating a duplicate chat")
      val response = listResponse
        .map(_.right.value)
        .flatMap(
          _.chats.headOption.collect {
            case ActiveChat(baseInfo, _, _) => client.request(UpdateChatRequest(baseInfo.iden, muted = true))
          }.value
        )
      Then("The chat should be deleted")
      response.map(_ should be ('right))
    }

    scenario("Unmuting chat") {
      Given("One chat existing")
      val listResponse = testStart.flatMap(start => client.request(ChatListRequest(modifiedAfter = Option(start))))
      When("Creating a duplicate chat")
      val response = listResponse
        .map(_.right.value)
        .flatMap(
          _.chats.headOption.collect {
            case ActiveChat(baseInfo, Some(true), _) => client.request(UpdateChatRequest(baseInfo.iden, muted = false))
          }.value
        )
      Then("The chat should be deleted")
      response.map(_ should be ('right))
    }

    scenario("Deleting chat") {
      Given("One chat existing")
      val listResponse = testStart.flatMap(start => client.request(ChatListRequest(modifiedAfter = Option(start))))
      When("Creating a duplicate chat")
      val response = listResponse
        .map(_.right.value)
        .flatMap(
          _.chats.headOption.collect {
            case ActiveChat(baseInfo, _, _) => client.request(DeleteChatRequest(baseInfo.iden))
          }.value
        )
      Then("The chat should be deleted")
      response.map(_ should be ('right))
    }

    scenario("Deleting inactive chat") {
      Given("One chat existing")
      val listResponse = testStart.flatMap(start => client.request(ChatListRequest(modifiedAfter = Option(start))))
      When("Creating a duplicate chat")
      val response = listResponse
        .map(_.right.value)
        .flatMap(
          _.chats.headOption.collect {
            case InactiveChat(baseInfo) => client.request(DeleteChatRequest(baseInfo.iden))
          }.value
        )
      Then("The chat should be deleted")
      response.map(result => result should be ('left))
    }
  }

  feature("Pushes api") {
    val client = new Client("https://api.pushbullet.com/v2", "o.i8br1Q7KYE3IXHEPY01i7b1Qz2Z3Mz6j")
    ignore("Pushing a file") {
      When("Requesting to list chats")
      val response = client.uploadFile("myPushedFile.png",
        ContentTypes.`text/plain(UTF-8)`,
        new File(getClass.getResource("/test.txt").toURI))
          .flatMap( resp =>
            resp.fold(_ => Future.successful(resp),
              uploadResp => {
                val pushData = PushData.File("This is my file push", PushData.FileData(uploadResp))
                client.request(CreatePushRequest(PushTarget.Broadcast, pushData))
              })
          )
      Then("The response should be empty")
      response.map(_ should be ('right))
    }

    scenario("Pushing a note") {
      When("Pushing a note")
      val response = client.request(CreatePushRequest(PushTarget.Broadcast, PushData.Note("my title", "my body")))
      Then("The response should be OK")
      response.map(_ should be ('right))
    }
  }
}
