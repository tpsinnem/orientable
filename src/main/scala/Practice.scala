// import scala.collection.JavaConversions._
// import akka.actor._
// import akka.transactor._
// import scala.concurrent.stm._

object Main extends App {
  println(OrientableExperiment.cRUD getOrElse("really fail"))
}

case class PracticeCaseClass(practiceString:String = "DefaultPracticeString")
object Practice {
}
