package zio2.elevator
import scala.Console.{BLUE, CYAN, GREEN, RED, RESET, YELLOW}
import ElevatorStrategy.{hasReachedStop, isHeadingDown, isHeadingUp}

import zio.nio.charset.Charset
import zio.nio.file.{Files, Path}

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import zio.ZIO
import scala.collection.mutable
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

case class ElevatorLog() {

  val entries: mutable.ListBuffer[ElevatorTripData] = mutable.ListBuffer[ElevatorTripData]()
  def add(s: ElevatorTripData):Unit = entries += s

  def logTotalTimeStats = {
    val totalTimeStats = new DescriptiveStatistics();
    entries.map(_.getTotalTime.map(totalTimeStats.addValue))
 
    val totalTimeHeaderStr = s"min;max;mean"
    val minStr = s"${totalTimeStats.getMin().toInt}"
    val maxStr = s"${totalTimeStats.getMax().toInt}"
    val meanStr = s"${totalTimeStats.getMean().toInt}"

    val totalTimeStatsStr = s"${totalTimeHeaderStr}\n${minStr};${maxStr};${meanStr}"

    totalTimeStatsStr
  }
}

object ElevatorLog {

  def apply = new ElevatorLog

  val header =
    "servedByElevator;pickedUpOnFloor;destinationFloor;floorDistance;totalTime[ms];fulfillmentTime[ms];waitingTime[ms]"

  val colorMap: Map[String, String] =
    Map("1" -> GREEN, "2" -> BLUE, "3" -> YELLOW)

  def logLine(elevator: Elevator): String = {
    val elevatorColor = colorMap(elevator.id)
    val floorRouteStr =
      if (elevator.floorStops.nonEmpty)
        s"""floorRoute: "${elevator.floorStops.mkString(", ")}""""
      else ""
    val hasReachedFloorColorStr =
      if (hasReachedStop(elevator)) RED else elevatorColor

    val directionStr =
      s"D:${isHeadingDown(elevator)}:U:${isHeadingUp(elevator)}"

      // {Elevator 3: floorRoute: "(⬆️: 14, stats: 3;0;-9999;-1;0.00;0.00;27)", 🏠 "6":D:false:U:true checking incoming queue

    val elevatorIdStr =
      s"${hasReachedFloorColorStr}{Elevator ${elevator.id}"

    if (hasReachedStop(elevator))
      s"${elevatorIdStr} 🏠 ${elevator.currentFloor}: Reached floor ${elevator.currentFloor}}$RESET"
    else
      s"${elevatorIdStr} 🏠 ${elevator.currentFloor}: ${floorRouteStr}}$RESET"

    // s"${elevatoridStr}: ${floorRouteStr}" +
    //   s"""🏠 "${elevator.currentFloor}, ${directionStr}" checking incoming queue${RESET}"""
  }

  def logElevatorStats(reachedStops: mutable.SortedSet[Request]) = {
    Files
      .writeLines(
        path = Path("logs.txt"),
        lines = reachedStops.map(req => s"${req.elevatorTripData}").toList,
        charset = Charset.defaultCharset,
        openOptions = Set(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      )
      .catchAll(t => ZIO.succeed(println(t.getMessage)))
  }

  def logHeader(logSink: Vector[ElevatorTripData]) = Files
    .writeLines(
      path = Path("logs.txt"),
      lines = List(header),
      charset = Charset.defaultCharset,
      openOptions = Set(StandardOpenOption.CREATE_NEW)
    )
    .catchAll(t => ZIO.succeed(println(t.getMessage)))

}
