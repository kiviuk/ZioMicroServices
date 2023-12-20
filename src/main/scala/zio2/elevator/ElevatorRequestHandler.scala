package zio2.elevator

import zio.nio.InetSocketAddress
import zio.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel}
import zio.stm.TPriorityQueue
import zio.{Console, ZIO}
import zio2.elevator.Decoder.decodeCommand
import zio2.elevator.Decoder.{Move, IncompleteCommand, UpRequest, DownRequest}
import zio.ZLayer
import zio2.elevator.LiveSocketService

import java.io.IOException

trait AsyncElevatorRequestHandlerTrait {
    val startHandlingRequests: ZIO[Any, IOException, Nothing]
}

case class AsyncElevatorRequestHandlerImpl(worker: ElevatorRequestWorkerTrait) extends AsyncElevatorRequestHandlerTrait {

  private val PORT = 7777
  private val HOST = "0.0.0.0"

  val startHandlingRequests: ZIO[Any, IOException, Nothing] = ZIO.scoped {
    AsynchronousServerSocketChannel.open
      .flatMap { socket =>
        for {
          address <- InetSocketAddress.hostName(HOST, PORT)
          _ <- socket.bindTo(address).orElseFail(
            new IOException(s"Couldn't bind the server socket to the address $HOST:$PORT")
          )
          _ <- socket.accept.either.flatMap {
            case Left(ex) =>
              Console.printLine(s"Failed to accept client connection: ${ex.getMessage}")
            case Right(channel) =>
              worker.doWork(LiveSocketService(channel)).catchAll(ex =>
                Console.printLine(s"Exception in handling client: ${ex.getMessage}")
              ).fork
          }.forever
        } yield ()
      } *> ZIO.never
  }
}

object ElevatorRequestHandler {
  def start(upQueue: TPriorityQueue[OutsideUpRequest],
            downQueue: TPriorityQueue[OutsideDownRequest],
            elevatorInsideQueue: TPriorityQueue[InsideElevatorRequest]*
            ) = {

    val worker: ElevatorRequestWorkerTrait = ElevatorRequestWorkerImpl(elevatorInsideQueue.toList, upQueue, downQueue)
    val elevatorRequestHandler = AsyncElevatorRequestHandlerImpl(worker)
    elevatorRequestHandler.startHandlingRequests
  }
}

object AsyncElevatorRequestHandlerLayer {
  val layer: ZLayer[ElevatorRequestWorkerTrait, Nothing, AsyncElevatorRequestHandlerTrait] = 
    ZLayer.fromZIO {
      for {
        worker <- ZIO.service[ElevatorRequestWorkerTrait]
      } yield AsyncElevatorRequestHandlerImpl(worker)
    }
}
