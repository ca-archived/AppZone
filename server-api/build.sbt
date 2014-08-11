import AssemblyKeys._

organization := "jp.co.cyberagent"

name := "AppZone"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.9.2"

seq(webSettings :_*)

classpathTypes ~= (_ + "orbit")

mainClass := Some("jp.co.cyberagent.appzone.JettyLauncher")

assemblySettings

libraryDependencies ++= Seq(
  "net.liftweb" %% "lift-mongodb-record" % "2.6-M2",
  "net.liftweb" %% "lift-json" % "2.6-M2",
  "net.liftweb" %% "lift-json-ext" % "2.6-M2",
  "org.scalatra" % "scalatra" % "2.1.1",
  "org.scalatra" % "scalatra-scalate" % "2.1.1",
  "org.scalatra" % "scalatra-scalatest" % "2.1.1" % "test",
  "org.scalatra" % "scalatra-specs2" % "2.1.1" % "test",
  "ch.qos.logback" % "logback-classic" % "1.1.1" % "runtime",
  "com.googlecode.plist" % "dd-plist" % "1.0",
  "xpp3" % "xpp3" % "1.1.4c",
  "com.jcraft" % "jzlib" % "1.1.2",
  "org.apache.commons" % "commons-io" % "1.3.2",
  "commons-net" % "commons-net" % "3.2",
  "org.eclipse.jetty" % "jetty-webapp" % "8.1.7.v20120910" % "compile;container",
  "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "compile;container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
)

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case "about.html" => MergeStrategy.rename
    case x => old(x)
  }
}

javacOptions ++= Seq("-encoding", "UTF-8")
