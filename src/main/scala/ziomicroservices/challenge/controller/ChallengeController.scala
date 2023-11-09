package ziomicroservices.challenge.controller

import zio.json.*
import zio.http.*
import ziomicroservices.challenge.model.Challenge
import ziomicroservices.challenge.service.ChallengeService

object ChallengeController {
  def apply(): Http[ChallengeService, Throwable, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> Root / "challenges" / "random" => {
        ChallengeService.createRandomMultiplication().map(response => Response.json(response.toJson))
      }
    }

}