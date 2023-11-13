package ziomicroservices.elevator.experiments
import zio.*

trait School {
  def findSchoolClass(name: String): Task[SchoolClass]
}

trait SchoolClass {
  def findClassMember(name: String): Task[ClassMember]
}

trait ClassMember {
  def name(): Task[String]
}

case class SchoolImpl(schoolClass: SchoolClass) extends School {
  def findSchoolClass(name: String): Task[SchoolClass] = {
    ZIO.succeed(SchoolClassImpl())
  }
}

case class SchoolClassImpl() extends SchoolClass {
  override def findClassMember(name: String): Task[ClassMember] = {
    val pupilOrTeacher = ClassMemberImpl("Susie")
    ZIO.succeed(pupilOrTeacher)
  }
}

case class ClassMemberImpl(pupilOrTeacherName: String) extends ClassMember {
  override def name(): Task[String] = ZIO.succeed(pupilOrTeacherName)
}

object School {
  def layer = ZLayer.fromFunction(SchoolImpl.apply _ )
}