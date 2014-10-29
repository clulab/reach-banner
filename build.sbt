name := Common.name

version := Common.version

organization := Common.organization

scalaVersion := "2.10.4"

lazy val core = project.in(file(".")).settings(
  addArtifact(Artifact(Common.name, Common.classifier), modelsTask in models).settings: _*
) aggregate models dependsOn models


lazy val models = project in file("models")

publishArtifact in (Compile, packageSrc) := false

publishArtifact in (Compile, packageDoc) := false

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % "2.10.4",
  "org.scalatest" %% "scalatest" % "2.0.M6-SNAP17",
  "junit" % "junit" % "4.10",
  "bsh" % "bsh" % "2.0b1",
  "jdom" % "jdom" % "1.0",
  "net.sf.jwordnet" % "jwnl" % "1.4_rc3"
)
