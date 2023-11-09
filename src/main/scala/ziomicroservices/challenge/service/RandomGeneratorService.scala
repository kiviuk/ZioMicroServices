package ziomicroservices.challenge.service

import zio._

// INTERFACE

trait RandomGeneratorService {
  def generateRandomFactor(): UIO[Int]
}

object RandomGeneratorService {
  def generateRandomFactor(): ZIO[RandomGeneratorService, Nothing, Int] =
    ZIO.serviceWithZIO[RandomGeneratorService](_.generateRandomFactor())
}




