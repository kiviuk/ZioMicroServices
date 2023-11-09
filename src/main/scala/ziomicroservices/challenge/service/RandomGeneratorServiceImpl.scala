package ziomicroservices.challenge.service

import zio._

// IMPLEMENTATIONS

case class RandomGeneratorServiceImpl() extends RandomGeneratorService {
  private val MINIMUM_FACTOR = 1
  private val MAXIMUM_FACTOR = 10

  def generateRandomFactor(): UIO[Int] =
    Random.nextIntBetween(MINIMUM_FACTOR, MAXIMUM_FACTOR)
}

object RandomGeneratorServiceImpl {
  val layer: ZLayer[Any, Nothing, RandomGeneratorServiceImpl] =
    ZLayer.succeed(RandomGeneratorServiceImpl())
}