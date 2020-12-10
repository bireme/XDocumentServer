lazy val root = (project in file("."))
  .settings(
    inThisBuild(List(
      organization := "org.bireme",
      scalaVersion :=  "2.12.11", //"2.12.8", "2.13.3", vertx não tem
      version      := "1.0.0"
    )),
    name := "XDocumentServer"
  )

val pdfboxVersion = "2.0.21" //"2.0.20"
val pdfboxAppVersion = "2.0.20" //"2.0.18"
val jpef2000Version =  "1.3.0"
val jaiImageioCoreVersion = "1.4.0"
val sttpVersion = "1.7.2" //"1.6.7"
val vertxVersion = "3.9.1" //"3.8.0" //"3.8.4" nao tem para scala 2.13
val solrCellVersion = "8.5.2" //"8.4.1"
val restletVersion = "2.4.3" //"2.4.2"
val httpComponentsVersion = "4.5.13" //"4.5.12"
val scalajHttpVersion = "2.4.2" //"2.4.1"
val circeVersion = "0.13.0" //"0.12.3"
val commonsIOVersion = "2.8.0" //"2.7"
val hasherVersion = "1.2.0"
val airframeVersion = "20.10.3" //"20.6.2"
//val hairyfotrVersion = "0.1.17"
val scalaTestVersion = "3.2.3" //"3.2.0"
val scalaXmlVersion = "1.2.0"
//val swaydbVersion = "0.10.9" // "0.6"
val playJsonVersion = "2.9.1" //"2.9.0"
val sqliteVersion = "3.32.3.2" //"3.32.3"
val slickVersion = "3.3.3" //"3.3.2"
val logbackVersion = "1.2.3"
val luceneVersion = "8.5.2" //"8.7.0"

resolvers += "Restlet Repositories" at "https://maven.restlet.org"

libraryDependencies ++= Seq(
  "org.apache.pdfbox" % "pdfbox" % pdfboxVersion,
  "org.apache.pdfbox" % "pdfbox-app" % pdfboxAppVersion,
//  "com.github.jai-imageio" % "jai-imageio-jpeg2000" % jpef2000Version,
//  "com.github.jai-imageio" % "jai-imageio-core" % jaiImageioCoreVersion,
//  "com.softwaremill.sttp" %% "core" % sttpVersion,
  "io.vertx" %% "vertx-lang-scala" % vertxVersion,
  "io.vertx" %% "vertx-web-scala" % vertxVersion,
  //"com.github.takezoe" %% "solr-scala-client" % solrScalaClientVersion,
 // "org.restlet.jee" % "org.restlet" % restletVersion,
 // "org.apache.solr" % "solr-cell" % solrCellVersion,
  //"org.apache.httpcomponents" % "httpclient" % httpComponentsVersion,
  "org.scalaj" %% "scalaj-http" % scalajHttpVersion,
  "io.circe" %% "circe-core" % circeVersion,
  //"io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "commons-io" % "commons-io" % commonsIOVersion,
  "com.roundeights" %% "hasher" % hasherVersion,
  "org.wvlet.airframe" %% "airframe-log" % airframeVersion,
  "org.scalatest" % "scalatest_2.12" % scalaTestVersion % "test",
  //"org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion,
  //"com.typesafe.play" %% "play-json" % playJsonVersion,
  //"org.xerial" % "sqlite-jdbc" % sqliteVersion,
  //"com.typesafe.slick" %% "slick" % slickVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  "org.apache.lucene" % "lucene-backward-codecs" % luceneVersion
  //"io.swaydb" %% "swaydb" % swaydbVersion
)

logBuffered in Test := false
trapExit := false

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Ywarn-unused")
//addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % hairyfotrVersion)

test in assembly := {}

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

