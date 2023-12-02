package zio2.elevator


object Decoder {

  sealed trait Command

  case class Move(elevatorId: Int, floor: Int) extends Command

  case class UpRequest(floor: Int) extends Command

  case class DownRequest(floor: Int) extends Command

  case class IncompleteCommand(input: String) extends Command

  /**
   * This method decodes a rawCommand string into a List of Command objects.
   *
   * The method supports decoding of three types of commands: Move, UpRequest and DownRequest.
   *
   * The commands are expected to be in specific string formats:
   * - Move command format is "m:<elevatorId>:<floor>", e.g. "m:2:3" stands for "Move Elevator[2] to Floor[3]".
   * - UpRequest command format is "r:<floor>:u", e.g. "r:1:u" stands for "Request an elevator to
   * Floor[1] with direction [up]".
   * - DownRequest command: Format is "r:<floor>:d".
   *
   * Any other format is considered as an incomplete command e.g. "m:1:" or "7" would be
   * received as "Received incomplete request [m:1:]" and "Received incomplete request [7]" respectively.
   *
   * @param rawCommand The raw string which contains commands. Each command in the string
   *                   should be separated by a pipe ("|"). For example: "r:1:u|m:2:3|7".
   * @return Returns a List of Command objects. Each command in the rawCommand string is
   *         transformed into an instance of one of the Command subclasses (Move, UpRequest,
   *         DownRequest, IncompleteCommand).
   *
   *         IncompleteCommand is used for commands that do not conform to the patterns of
   *         Move, UpRequest or DownRequest.
   * @example An example of usage can be seen from a terminal:
   *          echo "r:10:u|r:9:d|m:1:|m:2:3|7" | nc  -w 0 localhost 7777
   *
   *          Note: Floor numbers can be negative, 0, or positive.
   */
  def decodeCommand(rawCommand: String): List[Command] = {
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

    //println(s"Decoding: [$rawCommand]")

    val splitCommands = if (rawCommand == null)
      List("")
    else
      // without nonEmpty filter: IF rawCommand == "|r" split gives List(, "r") instead of List("r")
      rawCommand.strip.split("\\|").filter(_.nonEmpty).toList

    val completedCommands = if (splitCommands.nonEmpty) splitCommands.init else List.empty
    val remainingCommand = if (splitCommands.nonEmpty) splitCommands.last else ""

    val parsedCommands = completedCommands.map(parseCommand)

    val lastCommand = parseCommand(remainingCommand)

    parsedCommands :+ lastCommand
  }
}