
import com.typesafe.sbt.SbtScalariform
import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.SbtOsgi._

object SimPuzzleBuild extends Build {

  val monocleVersion = "0.4.0"  // or "0.5-SNAPSHOT"

  override def settings = 
   super.settings ++ Seq(
     scalaVersion := "2.11.2",
     crossScalaVersions := Seq("2.10.4", "2.11.2"),
     organization := "fr.geocite",
     publishTo := { 
       if (version.value.trim.endsWith("SNAPSHOT")) Some("ISCPIF Nexus snapshot" at "http://maven.iscpif.fr/snapshots") 
       else Some("ISCPIF Nexus" at "http://maven.iscpif.fr/releases")
     },
     libraryDependencies += "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
     libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.0.6",
     libraryDependencies ++= Seq(
       "com.github.julien-truffaut"  %%  "monocle-core"    % monocleVersion,
       "com.github.julien-truffaut"  %%  "monocle-generic" % monocleVersion,
       "com.github.julien-truffaut"  %%  "monocle-macro"   % monocleVersion
     ),
     libraryDependencies += (
       if (scalaVersion.value.startsWith("2.10")) "com.chuusai" %% "shapeless" % "2.0.0" cross CrossVersion.full else "com.chuusai" %% "shapeless" % "2.0.0"),
     libraryDependencies += "org.apache.commons" % "commons-math3" % "3.3",
     libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
     libraryDependencies ++= (
       if (scalaVersion.value.startsWith("2.10")) List("org.scalamacros" %% "quasiquotes" % "2.0.0")
       else Nil
     ),
     resolvers += "ISC-PIF" at "http://maven.iscpif.fr/public/"
   ) 

 lazy val geotools = "org.geotools" % "gt-referencing" % "9.3"

 lazy val simpuzzle = Project(id = "simpuzzle", base = file("simpuzzle"))

 lazy val gis = Project(id = "gis", base = file("gis")) dependsOn(simpuzzle) settings (libraryDependencies += geotools)


 lazy val simpoplocal = Project(id = "simpoplocal", base = file("models/simpoplocal")) dependsOn(simpuzzle)

 lazy val schelling = Project(id = "schelling", base = file("models/schelling")) dependsOn(simpuzzle)
 
 lazy val sugarscape = Project(id = "sugarscape", base = file("models/sugarscape")) dependsOn(simpuzzle)

 lazy val gibrat = Project(id = "gibrat", base = file("models/gibrat")) dependsOn(simpuzzle)


 lazy val marius = Project(id = "marius", base = file("models/marius/model")) dependsOn (simpuzzle, gibrat, gis)

 lazy val mariusrun = Project(id = "mariusrun", base = file("models/marius/run")) dependsOn(marius)

 lazy val mariuscalibration = Project(id = "mariuscalibration", base = file("models/marius/calibration"), settings = settings ++ osgiSettings) dependsOn(marius, mariusrun) settings (
    OsgiKeys.exportPackage := Seq("fr.geocites.marius.*"),
    OsgiKeys.importPackage := Seq("*;resolution:=optional"),
    OsgiKeys.privatePackage := Seq("!scala.*", "*")
   )

 lazy val mariusbehaviour = Project(id = "mariusbehaviour", base = file("models/marius/behaviour"), settings = settings ++ osgiSettings) dependsOn(marius, mariusrun) settings (
    OsgiKeys.exportPackage := Seq("fr.geocites.marius.*"),
    OsgiKeys.importPackage := Seq("*;resolution:=optional"),
    OsgiKeys.privatePackage := Seq("!scala.*", "*")
   )

 lazy val flocking = Project(id = "flocking", base = file("models/flocking/model"))

  lazy val flockingVisualisation = Project(id = "flockingvisualisation", base = file("models/flocking/visualisation")) settings {
    libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "1.0.1"
  } dependsOn(flocking)

  lazy val flockingbehaviour = Project(id = "flockingbehaviour", base = file("models/flocking/behaviour"), settings = settings ++ osgiSettings) dependsOn(flocking) settings (
    OsgiKeys.exportPackage := Seq("fr.iscpif.flocking.*"),
    OsgiKeys.importPackage := Seq("*;resolution:=optional"),
    OsgiKeys.privatePackage := Seq("!scala.*", "*")
    )

}

