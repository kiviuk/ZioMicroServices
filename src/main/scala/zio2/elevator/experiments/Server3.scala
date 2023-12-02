//package zio2.elevator
//
//import zio.*
//import zio.Console.*
//import zio.nio.channels.*
//import zio.nio.*
//import zio.parser.Printer.Succeed
//
//import java.io.IOException
//import java.util.concurrent.atomic.AtomicInteger
//
//object Server3 extends ZIOAppDefault {
//
//  case class Server3() {
//
//    val server: ZIO[Any, IOException, Nothing] = ZIO.scoped {
//      AsynchronousServerSocketChannel.open
//        .flatMap { socket =>
//          for {
//            address <- InetSocketAddress.hostName("127.0.0.1", 1337)
//            service <- TestSocketService()
//            _ <- socket.bindTo(address)
//            _ <- socket.accept.flatMap(channel =>
//              doWork(service).catchAll(ex => printLine(ex.getMessage)).fork
//            ).forever.fork
//          } yield ()
//        } *> ZIO.never
//    }
//
//    def doWork(channel: SocketService): ZIO[Any, Throwable, Unit] = {
//      val process =
//        for {
//          chunk <- channel.readChunk(30)
//          str = chunk.toArray.map(_.toChar).mkString
//          _ <- printLine(s"received: [$str] [${chunk.length}]")
//        } yield ()
//
//      process.whenZIO(channel.isOpen).forever
//    }
//
//    val clientM: ZIO[Scope, Exception, AsynchronousSocketChannel] = AsynchronousSocketChannel.open
//      .flatMap { client =>
//        for {
//          host <- InetAddress.localHost
//          address <- InetSocketAddress.inetAddress(host, 1337)
//          _ <- client.connect(address)
//        } yield client
//      }
//
//    def start =
//      for {
//        serverFiber <- server.fork
//        _ <- ZIO.scoped(clientM.flatMap(_.writeChunk(Chunk.fromArray(Array(65, 66).map(_.toByte)))))
//        _ <- serverFiber.join
//      } yield ()
//
//  }
//
//  val server = Server3()
//
//
//  def run: ZIO[Any, Exception, ExitCode] =
//    for {
//      _ <- server.start
//      // handle termination signal
//      _ <- ZIO.never.onInterrupt(ZIO.succeed(println("Server terminating.")))
//    } yield ExitCode.success
//}
//
/////////////////////////////////////////////////////////////////////////////////////////////////////
//
//class TestSocketService(inputDataStream: List[Chunk[Byte]]) extends SocketService {
//  val count: AtomicInteger = AtomicInteger(0)
//
//  override def readChunk(capacity: Int): Task[Chunk[Byte]] = {
//
//    if (count.get() < inputDataStream.size)
//      println("A")
//      val d = inputDataStream(count.getAndIncrement)
//      ZIO.succeed(d)
//    else
//      println("B")
//      ZIO.succeed(Chunk.empty)
//  }
//
//  override def isOpen: Task[Boolean] = ZIO.succeed(false) // ZIO.succeed(count.get() < inputDataStream.size)
//}
//
//object TestSocketService {
//  def apply(): ZIO[Any, Nothing, SocketService] = {
//    val input = Array(65, 66, 67)
//    val inputDataStream: List[Chunk[Byte]] = input.map { char => Chunk.single(char.toByte) }.toList
//    val service: SocketService = new TestChaannelService(inputDataStream)
//    ZIO.succeed(service)
//  }
//}