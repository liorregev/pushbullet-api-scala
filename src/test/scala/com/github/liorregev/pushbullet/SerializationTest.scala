package com.github.liorregev.pushbullet

import com.github.liorregev.pushbullet.domain.Push
import org.scalatest.{EitherValues, FunSuite, Matchers}
import play.api.libs.json.Json

class SerializationTest extends FunSuite with Matchers with EitherValues{
  test("parse new push") {
    val data=
      """
        |{
        |	"active": true,
        |	"iden": "ujCGyGbJ2DQsjAqXAv0sTI",
        |	"created": 1547128667.2207136,
        |	"modified": 1547128667.231794,
        |	"type": "note",
        |	"dismissed": false,
        |	"direction": "self",
        |	"sender_iden": "ujCGyGbJ2DQ",
        |	"sender_email": "lioregev@gmail.com",
        |	"sender_email_normalized": "lioregev@gmail.com",
        |	"sender_name": "Lior Regev",
        |	"receiver_iden": "ujCGyGbJ2DQ",
        |	"receiver_email": "lioregev@gmail.com",
        |	"receiver_email_normalized": "lioregev@gmail.com",
        |	"title": "my title",
        |	"body": "my body"
        |}
      """.stripMargin
    Json.parse(data).validate[Push].asEither should be ('right)
  }

}
