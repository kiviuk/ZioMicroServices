package ziomicroservices.elevator

import zio.http.*
import zio.{json, *}
import zio.json.*
import ziomicroservices.elevator.model.Elevator
import ziomicroservices.elevator.service.elevator.ElevatorService

object MainOld  {
//
//  object Elevator2 {
//    implicit val encoder: JsonEncoder[Elevator] =
//      DeriveJsonEncoder.gen[Elevator]
//  }
//
//  import zio.json.EncoderOps
//
//  val app = Routes(
//    Method.GET / "elevator" / string("id") -> handler {
//      (id: String, req: Request) => {
//
//        val elevatorZio: ZIO[ElevatorService, NoSuchElementException, Elevator] =
//          ElevatorService.findElevatorById(id)
//
//        val elevatorResponse: ZIO[ElevatorService, NoSuchElementException, Response] =
//          elevatorZio.map(e => Response.json(e.toJson))
//
//        elevatorResponse
//
//      }
//    }
//  ).toHttpApp
//
//  val server: ZIO[Scope, Throwable, Nothing] =
//    Server.serve(app).provide(Server.default)
//
//  val run =
//    for {
//      _ <- server.fork
//      _ <- Console.readLine("Press any key to exit...")
//    } yield ()
}



//object Main extends ZIOAppDefault {

// https://github.com/rajcspsg/zio-http/blob/77e1c8f3083e5efb70f405ca94fef1357fc7e1a1/zio-http-example/src/main/scala/example/CliExamples.scala#L27C63-L27C63
// https://github.com/zio/zio-http/blob/93d8229bacb9c7f4079fbe5cdf86bd10d2acde1d/zio-http-example/src/main/scala/example/EndpointExamples.scala#L27
//  val getElevatorId = Endpoint(Method.GET / "elevator" / string("id")).out[Elevator]
// https://github.com/987Nabil/zio-http/blob/8780eb952f8819c7d42d88be0a9a39d6dc2f8eeb/zio-http-benchmarks/src/main/scala-2.13/zio/http/benchmarks/EndpointBenchmark.scala#L93
// https://www.javadoc.io/doc/dev.zio/zio-http_3/latest/index.html

//  val app = Routes(
//    Method.GET / "elevator" / string("id") -> handler {
//      (id:String, req: Request) => {
//        ElevatorService.findElevatorById(id).map(elevator => Response.json(elevator.toJson))
//      }
//    }
//  ).toHttpApp

//  import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder, EncoderOps}
//  val app = Routes(
//    Method.GET / "elevator" / string("id") -> handler {
//      (id: String, req: Request) => {
//        val elevatorZio: ZIO[ElevatorService, NoSuchElementException, Elevator] = ElevatorService.findElevatorById(id)
//        val elevatorResponse: ZIO[ElevatorService, NoSuchElementException, Response] = elevatorZio.map(e => Response.json(e.toJson))
//        ZIO.succeed(Response.ok)
//      }
//    }
//  ).toHttpApp
//
//  val server: ZIO[Environment with ZIOAppArgs with Scope, Throwable, Any] =
//    Server.serve(app).provide(Server.defaultWithPort(7777))
//
//  val run =
//    for {
//      _ <- server.fork
////      y <- ElevatorServiceImpl.run
////      c <- y.findElevatorById("1")
////      _ <- Console.printLine(c)
//      _ <- Console.readLine("Press any key to exit...")
//    } yield ()
//}