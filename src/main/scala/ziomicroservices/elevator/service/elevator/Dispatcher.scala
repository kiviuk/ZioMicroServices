package ziomicroservices.elevator.service.elevator

import zio.nio.InetSocketAddress
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.nio.channels.AsynchronousSocketChannel
import zio.stm.TPriorityQueue
import zio.{Console, Scope, ZIO}
import ziomicroservices.elevator.model.{InsideRequest, OutsideDownRequest, OutsideUpRequest}

import java.io.IOException

case class DispatcherImpl(queues: List[TPriorityQueue[InsideRequest]],
                          ups: TPriorityQueue[OutsideUpRequest],
                          downs: TPriorityQueue[OutsideDownRequest]
                         ) {

  val server: ZIO[Any, IOException, Nothing] = ZIO.scoped {
    AsynchronousServerSocketChannel.open
      .flatMap { socket =>
        for {
          address <- InetSocketAddress.hostName("127.0.0.1", 7777)
          _ <- socket.bindTo(address)
          _ <- socket.accept.either.flatMap {
            case Left(ex) =>
              Console.printLine(s"Failed to accept client connection: ${ex.getMessage}")
            case Right(channel) =>
              Console.printLine(s"Accepted a client connection") *>
                doWork(channel).catchAll(
                  ex => Console.printLine(s"Exception in handling client: ${ex.getMessage}")).fork
          }.forever.fork
        } yield ()
      }.catchAll(ex => Console.printLine(s"Failed to establish server: ${ex.getMessage}")) *> ZIO.never
  }

  sealed trait Command

  private case class Move(elevatorId: Int, floor: Int) extends Command

  private case class UpRequest(floor: Int) extends Command

  private case class DownRequest(floor: Int) extends Command

  private case class IncompleteCommand(input: String) extends Command

  /**
   * This method decodes a rawCommand string into a List of Command objects.
   *
   * The method supports decoding of three types of commands: Move, UpRequest and DownRequest.
   *
   * The commands are expected to be in specific string formats:
   * - Move command format is "m:<elevatorId>:<floor>", e.g. "m:2:3" stands for "Move Elevator[2] to Floor[3]".
   * - UpRequest command format is "r:<floor>:u", e.g. "r:1:u" stands for "Request an elevator to
   *   Floor[1] with direction [up]".
   * - DownRequest command: Format is "r:<floor>:d".
   *
   * Any other format is considered as an incomplete command e.g. "m:1:" or "7" would be
   * received as "Received incomplete request [m:1:]" and "Received incomplete request [7]" respectively.
   *
   * @param rawCommand The raw string which contains commands. Each command in the string
   *                   should be separated by a pipe ("|"). For example: "r:1:u|m:2:3|7".
   *
   * @return Returns a List of Command objects. Each command in the rawCommand string is
   *         transformed into an instance of one of the Command subclasses (Move, UpRequest,
   *         DownRequest, IncompleteCommand).
   *
   *         IncompleteCommand is used for commands that do not conform to the patterns of
   *         Move, UpRequest or DownRequest.
   *
   * @example An example of usage can be seen from a terminal:
   *          echo "r:10:u|r:9:d|m:1:|m:2:3|7" | nc  -w 0 localhost 7777
   *
   * Note: Floor numbers can be negative, 0, or positive.
   */
  private def decode(rawCommand: String): List[Command] = {
    val MoveCommand = """m:(\d+):(-?\d+)""".r
    val UpRequestCommand = """r:(-?\d+):u""".r
    val DownRequestCommand = """r:(-?\d+):d""".r

    def parseCommand(input: String): Command = input match {
      case MoveCommand(elevatorId, floor) =>
        Move(elevatorId.toInt, floor.toInt)

      case UpRequestCommand(floor) =>
        UpRequest(floor.toInt)

      case DownRequestCommand(floor) =>
        DownRequest(floor.toInt)

      case _ =>
        IncompleteCommand(input)
    }

    // Using 'split' with -1 as the second argument retains trailing empty strings:
    // "r:1:up|m:1:3|".split("\\|", -1)   => Array(r:1:up, m:1:3, "")
    // Using 0 as the second argument discards trailing empty strings:
    // "r:1:up|m:1:3|".split("\\|", 0)    => Array(r:1:up, m:1:3)
    // If there's no split character, the whole input is treated as a single entity:
    // "r:1:up".split("\\|", 0)           => Array(r:1:up)
    val splitCommands = rawCommand.split("\\|", 0).map(_.strip)
    val completedCommands = splitCommands.init.toList
    val remainingCommand = splitCommands.last

    val parsedCommands = completedCommands.map(parseCommand)

    val lastCommand = parseCommand(remainingCommand)

    parsedCommands :+ lastCommand

  }

  def doWork(channel: AsynchronousSocketChannel): ZIO[Any, Throwable, Unit] = {

    def process(acc: String): ZIO[Any, Throwable, Unit] = {
      for {
        chunk <- channel.readChunk(3)
        cmd = acc + chunk.toArray.map(_.toChar).mkString

        _ <- ZIO.foreachDiscard(decode(cmd)) {
          case Move(elevatorId, floor) =>
            println(s"Moving 🛗 [$elevatorId] to 🏠 [$floor]")
            queues(elevatorId - 1).offer(InsideRequest(floor)).commit
          case UpRequest(floor) =>
            println(s"Requesting ⬆️ direction for 🏠 [$floor]")
            ups.offer(OutsideUpRequest(floor)).commit
          case DownRequest(floor) =>
            println(s"Requesting ⬇️ direction for 🏠 [$floor]")
            downs.offer(OutsideDownRequest(floor)).commit
          case IncompleteCommand(cmd) =>
            process(cmd)
        }
      } yield ()
    }

    Console.printLine("Accepted a client connection, start working")
      *> process("").whenZIO(channel.isOpen).forever
  }
}

object Dispatcher {
  def start(
             queues: List[TPriorityQueue[InsideRequest]],
             ups: TPriorityQueue[OutsideUpRequest],
             downs: TPriorityQueue[OutsideDownRequest]
           ): ZIO[Scope, IOException, Unit] = {
    val dispatcher = DispatcherImpl(queues, ups, downs)
    dispatcher.server
  }
}