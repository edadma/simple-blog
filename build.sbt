name          := "simple-blog"

version       := "0.1"

organization  := "ca.hyperreal"

scalaVersion  := "2.11.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
	"io.spray"            %%  "spray-json"    % "1.3.1",
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "2.3.11" % "test"
  )
}

libraryDependencies ++= Seq(
//	"com.github.mauricio" %% "postgresql-async" % "0.2.15",
	"org.mongodb" %% "casbah" % "2.7.4",
	"org.slf4j" % "slf4j-api" % "1.7.7",
	"org.slf4j" % "slf4j-simple" % "1.7.7"
	)

libraryDependencies ++= Seq(
	"org.webjars" % "bootstrap" % "3.3.1",
//	"org.webjars" % "angularjs" % "1.3.4-1"
	"org.webjars" % "backbonejs" % "1.1.2-2"
	)

mainClass in assembly := Some( "ca.hyperreal.Boot" )

jarName in assembly := "simple-blog-" + version.value + ".jar"

Revolver.settings

//lazy val root = (project in file(".")).enablePlugins(SbtTwirl)

seq(sassSettings : _*)

(resourceManaged in (Compile, SassKeys.sass)) <<= (crossTarget in Compile)(_ / "classes" / "public")

SassKeys.sassOutputStyle in (Compile, SassKeys.sass) := 'compressed

(compile in Compile) <<= compile in Compile dependsOn (SassKeys.sass in Compile)

seq(coffeeSettings: _*)

(resourceManaged in (Compile, CoffeeKeys.coffee)) <<= (crossTarget in Compile)(_ / "classes" / "public" / "js")

(CoffeeKeys.bare in (Compile, CoffeeKeys.coffee)) := true

publishMavenStyle := true

publishTo := Some( Resolver.sftp( "private", "hyperreal.ca", "/var/www/hyperreal.ca/maven2" ) )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

homepage := Some(url("https://github.com/edadma/simple-blog"))

pomExtra := (
  <scm>
    <url>git@github.com:edadma/simple-blog.git</url>
    <connection>scm:git:git@github.com:edadma/simple-blog.git</connection>
  </scm>
  <developers>
    <developer>
      <id>edadma</id>
      <name>Edward A. Maxedon, Sr.</name>
      <url>http://hyperreal.ca</url>
    </developer>
  </developers>)
