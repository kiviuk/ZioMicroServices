package ziomicroservices.elevator.controller

import zio.json.*
import zio.http.*
import ziomicroservices.elevator.model.Elevator
import ziomicroservices.elevator.service.elevator.ElevatorService
import ziomicroservices.elevator.model._

object ElevatorController {
  def apply(): Http[ElevatorService, Throwable, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> Root / "elevators" / id =>
        ElevatorService.findElevatorById(id).map(response => Response.json(response.toJson))
    }
}