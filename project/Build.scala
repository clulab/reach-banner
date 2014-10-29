import sbt._
import Keys._

object Common {
  def name = "banner"
  def version = "1.0-SNAPSHOT"
  def classifier = "models"
  def organization = "edu.arizona.sista"
}

object ProcessorsBuild extends Build {
  val modelsTask = TaskKey[File]("models-task")
}
