package zio2.elevator

import java.time.{Duration, Instant}

case class ElevatorTripData(servedByElevator: Option[String] = None,
                             createdAt: Instant = Instant.now,
                             pickedUpAt: Option[Instant] = None,
                             droppedOffAt: Option[Instant] = None,
                             pickedUpOnFloor: Option[Int] = None,
                             droppedOffAtFloor: Option[Int] = None) {

  private def getMillisBetween(first: Instant, second: Option[Instant]): Option[Double] =
    second.map(s => Duration.between(first, s).toMillis.toDouble)

  def getTotalTime: Option[Double] = getMillisBetween(createdAt, droppedOffAt)

  def getTripTimeInMillis: Option[Double] = pickedUpAt.flatMap(pt => getMillisBetween(pt, droppedOffAt))

  def getWaitingTime: Option[Double] = getMillisBetween(createdAt, pickedUpAt)

  private def getServedByElevator: String = servedByElevator.getOrElse("NaN")

  private def getPickedUpOnFloor: Int = pickedUpOnFloor.getOrElse(Int.MaxValue)

  private def getDroppedOffAtFloor: Int = droppedOffAtFloor.getOrElse(Int.MaxValue)

  def getFloorDistance: Option[Int] =
    for {
      puof <- pickedUpOnFloor
      df <- droppedOffAtFloor
    } yield Math.abs(puof - df)

  override def toString: String = {
    val totalTimeStr = getTotalTime.map(t => s"$t").getOrElse("0.00")
    val tripTime = getTripTimeInMillis.map(t => s"$t").getOrElse("0.00")
    val waitingTimeStr = getWaitingTime.map(t => s"$t").getOrElse("0.00")
    val floorDistance = getFloorDistance.map(d => s"$d").getOrElse("-1")
    s"$getServedByElevator; PickedUpOnFloor: $getPickedUpOnFloor; DroppedOffAtFloor: $getDroppedOffAtFloor; floorDistance: $floorDistance; totalTime: $totalTimeStr; tripTime: $tripTime; waitingTime: $waitingTimeStr"
  }
}