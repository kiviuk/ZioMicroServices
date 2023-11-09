package ziomicroservices.challenge.model

case class Elevator(id: String)
case class ElevatorMeta(elevator: Elevator, currentFloor: CurrentFloor, destination: DestinationFloor)
case class DestinationFloor(number: Int)
case class CurrentFloor(number: Int)