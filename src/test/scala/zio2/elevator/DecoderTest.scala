package zio2.elevator

import zio.test.*
import zio.test.Assertion.{contains, equalTo, hasSize, exists}
import zio2.elevator.Decoder.{Command, DownRequest, IncompleteCommand, Move, UpRequest}

object DecoderTest extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("DecoderTest")(

    test("Decoder should correctly decode valid commands") {
      val rawCommand = "m:1:5|r:5:u|r:4:d|m:2:6"
      val result = Decoder.decodeCommand(rawCommand)
      assert(result)(hasSize(equalTo(4)) &&
        contains(Move(1, 5)) &&
        contains(UpRequest(5)) &&
        contains(DownRequest(4)) &&
        contains(Move(2, 6))
      )
    },

    test("Decoder should correctly decode commands with negative floor numbers") {
      val rawCommand = "r:-4:d|m:2:6"
      val result = Decoder.decodeCommand(rawCommand)
      assert(result)(hasSize(equalTo(2)) &&
        contains(DownRequest(-4)) &&
        contains(Move(2, 6))
      )
    },

    test("Decoder should correctly decode commands with leading pipe symbol") {
      val rawCommand = "|m:2:6"
      val result = Decoder.decodeCommand(rawCommand)
      assert(result)(hasSize(equalTo(1)) &&
        contains(Move(2, 6))
      )
    },

    test("Decoder should correctly decode commands with trailing pipe symbol") {
      val rawCommand = "m:2:6|"
      val result = Decoder.decodeCommand(rawCommand)
      assert(result)(hasSize(equalTo(1)) &&
        contains(Move(2, 6))
      )
    },

    test("Decoder should correctly decode commands between pipe symbols") {
      val rawCommand = "|m:2:6|"
      val result = Decoder.decodeCommand(rawCommand)
      assert(result)(hasSize(equalTo(1)) &&
        contains(Move(2, 6))
      )
    },

    test("Decoder should correctly decode commands between pipe symbols") {
      val rawCommand = "m:3:-3|m:3:14|m:1:11|m:2:24|m:3:21|m:3:16|m:1:4|m:2:-2|m:2:123|m:3:3|m:3:3"
      val result = Decoder.decodeCommand(rawCommand)
      assert(result)(hasSize(equalTo(11)))
    },


    test("Decoder should return IncompleteCommand for null commands") {
      val rawCommand: Null = null
      val result: List[Command] = Decoder.decodeCommand(rawCommand)
      assertTrue(result.exists {
        case IncompleteCommand("") => true
        case _ => false
      })
    },

    test("Decoder should return IncompleteCommand for invalid commands") {
      val rawCommand = "m:1:5|r:5:u|invalid|m:2:6"
      val result: List[Command] = Decoder.decodeCommand(rawCommand)
      assert(result)(contains(IncompleteCommand("invalid")))
    },

    test("Decoder should return IncompleteCommand for invalid commands with leading pipe symbol") {
      val rawCommand = "|invalid"
      val result: List[Command] = Decoder.decodeCommand(rawCommand)
      assert(result)(contains(IncompleteCommand("invalid")))
    },

    test("Decoder should return IncompleteCommand for invalid commands with only pipe symbols") {
      val rawCommand1 = "|"
      val rawCommand2 = "||"
      val result1: List[Command] = Decoder.decodeCommand(rawCommand1)
      val result2: List[Command] = Decoder.decodeCommand(rawCommand2)
      assert(result1)(contains(IncompleteCommand("")))
      assert(result2)(contains(IncompleteCommand("")))
    },

  )
}