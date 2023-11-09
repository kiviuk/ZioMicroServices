package ziomicroservices.challenge.service

import zio._
import ziomicroservices.challenge.model.Challenge

// INTERFACE

trait ChallengeService:
  def createRandomMultiplication(): UIO[Challenge]

object ChallengeService:
  def createRandomMultiplication(): ZIO[ChallengeService, Nothing, Challenge] =
    ZIO.serviceWithZIO[ChallengeService](_.createRandomMultiplication())

// IMPLEMENTATIONS

case class ChallengeServiceImpl(randomGeneratorService: RandomGeneratorService) extends ChallengeService {
  override def createRandomMultiplication(): UIO[Challenge] = {
    for {
      i <- randomGeneratorService.generateRandomFactor()
      k <- randomGeneratorService.generateRandomFactor()
    } yield Challenge(i, k)
  }
}

// DEPENDENCIES

object ChallengeServiceImpl {
  def layer: ZLayer[RandomGeneratorService, Nothing, ChallengeServiceImpl] = ZLayer {
    for {
      generator <- ZIO.service[RandomGeneratorService]
    } yield ChallengeServiceImpl(generator)
  }
}