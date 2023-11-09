package ziomicroservices.challenge.model

import zio._
import zio.test._
import zio.json._

object ChallengeTest extends ZIOSpecDefault {

  def spec = {
    suite("Challenge Encode / Decoder")(
      test("converts from class to json") {
        assertTrue(
          Challenge(3, 2).toJson == """{"valueA":3,"valueB":2}"""
        )
      },
      test("converts from json to class") {
        assertTrue(
          """{"valueA":3,"valueB":2}""".fromJson[Challenge].getOrElse(null) == Challenge(3, 2)
        )
      }
    )
  }

}