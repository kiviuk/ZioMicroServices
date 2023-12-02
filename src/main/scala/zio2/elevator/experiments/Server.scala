package zio2.elevator.experiments

import zio.*
import zio.Console.*
import zio.nio.*
import zio.nio.channels.*
import zio2.elevator.experiments.{TestChannel, TestChannelService}

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

object Server extends ZIOAppDefault {

  case class Server() {

    val value: ZIO[Scope, IOException, Nothing] = AsynchronousServerSocketChannel.open
      .flatMap { socket =>
        for {
          address <- InetSocketAddress.hostName("127.0.0.1", 1337)
          _ <- socket.bindTo(address)
          _ <- socket
            .accept
            .flatMap(
              channel => doWork(TestChannel(channel)).catchAll(ex => printLine(ex.getMessage)).fork
            )
            .forever.fork
        } yield ()
      } *> ZIO.never

    val server = ZIO.scoped {
      value
    }

    def doWork(channel: TestChannelService): ZIO[Any, Throwable, Unit] = {
      val process: ZIO[Any, Throwable, Unit] =
        for {
          chunk <- channel.readChunk(3)
          str = chunk.toArray.map(_.toChar).mkString
          _ <- printLine(s"received: [$str] [${chunk.length}]")
        } yield ()

      for {
        _ <- process.repeatWhileZIO(_ => channel.isOpen.catchAll(_ => ZIO.succeed(false)))
      } yield ()
    }

    val clientM: ZIO[Scope, Exception, AsynchronousSocketChannel] = AsynchronousSocketChannel.open
      .flatMap { client =>
        for {
          host <- InetAddress.localHost
          address <- InetSocketAddress.inetAddress(host, 1337)
          _ <- client.connect(address)
        } yield client
      }

    def start =
      for {
        serverFiber <- server.fork
        _ <- ZIO.scoped(clientM.flatMap(_.writeChunk(Chunk.fromArray("12345678".toCharArray.map(_.toByte)))))
        _ <- serverFiber.join
      } yield ()
  }

  val server = Server()

  def run: ZIO[Any, Exception, ExitCode] =
    for {
      _ <- server.start
      _ <- ZIO.never.onInterrupt(ZIO.succeed(println("Server terminating.")))
    } yield ExitCode.success

}

trait TestChannelService {
  def readChunk(capacity: Int): Task[Chunk[Byte]]

  def isOpen: Task[Boolean]
}

case class TestChannel(channel: AsynchronousSocketChannel) extends TestChannelService {
  val count: AtomicInteger = AtomicInteger(0)

  override def readChunk(capacity: Int): Task[Chunk[Byte]] = channel.readChunk(capacity)

  override def isOpen: Task[Boolean] = ZIO.succeed(count.getAndIncrement() < 0)  <* Console.printLine(count.get()-1)

}

object TestChannel {
  def apply(channel: AsynchronousSocketChannel) = new TestChannel(channel)
}