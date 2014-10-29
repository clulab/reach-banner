name := "banner"

version := "1.0-SNAPSHOT"

organization := "edu.arizona.sista"

scalaVersion := "2.10.4"

dragontoolTask := { file("lib/dragontool.jar") }

heptagTask := { file("lib/heptag.jar") }

addArtifact(Artifact("banner", "dragontool"), dragontoolTask)

addArtifact(Artifact("banner", "heptag"), heptagTask)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.0.M6-SNAP17" % "test",
  "junit" % "junit" % "4.10",
  "bsh" % "bsh" % "2.0b1",
  "jdom" % "jdom" % "1.0",
  "net.sf.jwordnet" % "jwnl" % "1.4_rc3"
)
