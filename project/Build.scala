import sbt._
import Keys._

object BannerBuild extends Build {
  val dragontoolTask = TaskKey[File]("dragontool")
  val heptagTask = TaskKey[File]("heptag")
}
