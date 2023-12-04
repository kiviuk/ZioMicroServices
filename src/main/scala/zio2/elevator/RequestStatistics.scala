package zio2.elevator

import java.time.{Duration, Instant}

case class RequestStatistics(servedByElevator: Option[String] = None,
                             createdAt: Instant = Instant.now,
                             pickedUpAt: Option[Instant] = None,
                             dequeuedAt: Option[Instant] = None,
                             pickedUpOnFloor: Option[Int] = None,
                             destinationFloor: Option[Int] = None) {

  private def getMillisBetween(first: Instant, second: Option[Instant]): Option[Long] =
    second.map(s => Duration.between(first, s).toMillis)

  private def getTotalTime: Option[Long] = getMillisBetween(createdAt, dequeuedAt)

  private def getFulfillmentTime: Option[Long] = pickedUpAt.flatMap(pt => getMillisBetween(pt, dequeuedAt))

  private def getWaitingTime: Option[Long] = getMillisBetween(createdAt, pickedUpAt)

  private def getServedByElevator: String = servedByElevator.getOrElse("NaN")

  private def getPickedUpOnFloor: Int = pickedUpOnFloor.getOrElse(-9999)

  private def getDestinationFloor: Int = destinationFloor.getOrElse(-9999)

  private def getFloorDistance: Option[Int] =
    for {
      puof <- pickedUpOnFloor
      df <- destinationFloor
    } yield Math.abs(puof - df)

  override def toString: String = {
    val totalTimeStr = getTotalTime.map(t => s"$t").getOrElse("0.00")
    val fulfillmentTimeStr = getFulfillmentTime.map(t => s"$t").getOrElse("0.00")
    val waitingTimeStr = getWaitingTime.map(t => s"$t").getOrElse("0.00")
    val floorDistance = getFloorDistance.map(d => s"$d").getOrElse("-1")
    s"$getServedByElevator;$getPickedUpOnFloor;$getDestinationFloor;$floorDistance;$totalTimeStr;$fulfillmentTimeStr;$waitingTimeStr"
  }
}