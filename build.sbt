name := "banner"

version := "1.0-SNAPSHOT"

organization := "edu.arizona.sista"

scalaVersion := "2.10.4"

scalacOptions += "-target:jvm-1.6"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

//
// publishing settings
//

// other jars required by BANNER
dragontoolTask := { file("lib/dragontool.jar") }
heptagTask := { file("lib/heptag.jar") }
addArtifact(Artifact("banner", "dragontool"), dragontoolTask)
addArtifact(Artifact("banner", "heptag"), heptagTask)

// we should always publish sources!
publishArtifact in (Compile, packageSrc) := true

// there is no javadoc for scala projects, so we can skip this
publishArtifact in (Compile, packageDoc) := false

// publish to a maven repo
publishMavenStyle := true

// the standard maven repository
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

// let’s remove any repositories for optional dependencies in our artifact
pomIncludeRepository := { _ => false }

// mandatory stuff to add to the pom for publishing
pomExtra := (
  <url>https://github.com/sistanlp/banner</url>
  <licenses>
    <license>
      <name>Apache License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
	<url>https://github.com/sistanlp/banner</url>
	<connection>https://github.com/sistanlp/banner</connection>
  </scm>
  <developers>
  	<developer>
		<id>mihai.surdeanu</id>
		<name>Mihai Surdeanu</name>
		<email>mihai@surdeanu.info</email>
	</developer>
  </developers>)

//
// end publishing settings
//

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.0.M6-SNAP17" % "test",
  "junit" % "junit" % "4.10",
  "bsh" % "bsh" % "2.0b1",
  "jdom" % "jdom" % "1.0",
  "net.sf.jwordnet" % "jwnl" % "1.4_rc3"
)
