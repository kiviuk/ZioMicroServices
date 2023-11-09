package ziomicroservices.challenge.service

import zio._
import zio.test._
import zio.test.Assertion.equalTo

object RandomGeneratorServiceImplTest extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = {
    suite("Test RandomGeneratorServiceImpl") (
      test("should provide random number back") {
        for {
          _ <- TestRandom.setSeed(42L)
          mul <- RandomGeneratorServiceImpl().generateRandomFactor()

        } yield assert(mul)(equalTo(9))
      }
    )
  }
}