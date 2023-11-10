package ziomicroservices.elevator

import zio.http.Server
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
import ziomicroservices.elevator.controller.ElevatorController
import ziomicroservices.elevator.service.elevator.ElevatorServiceImpl

object Main extends ZIOAppDefault  {

  def run: ZIO[Environment with ZIOAppArgs with Scope, Throwable, Any] =
    val httpApps = ElevatorController()
    Server
      .serve(httpApps.withDefaultErrorResponse
      )
      .provide(
        Server.defaultWithPort(7777),
        ElevatorServiceImpl.layer,
      )
}