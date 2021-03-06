name := "pushbullet-api"

version := "0.1"

scalaVersion := "2.12.8"

scalacOptions ++= Seq(
  "-feature", "-deprecation", "-unchecked", "-explaintypes",
  "-encoding", "UTF-8", // yes, this is 2 args
  "-language:reflectiveCalls", "-language:implicitConversions", "-language:postfixOps", "-language:existentials",
  "-language:higherKinds",
  // http://blog.threatstack.com/useful-scala-compiler-options-part-3-linting
  "-Xcheckinit", "-Xexperimental", "-Xfatal-warnings", /*"-Xlog-implicits", */"-Xfuture", "-Xlint",
  "-Ywarn-dead-code", "-Ywarn-inaccessible", "-Ywarn-numeric-widen", "-Yno-adapted-args", "-Ywarn-unused-import",
  "-Ywarn-unused", "-Ypartial-unification"
)

wartremoverErrors ++= Seq(
  Wart.StringPlusAny, Wart.FinalCaseClass, Wart.JavaConversions, Wart.Null, Wart.Product, Wart.Serializable,
  Wart.LeakingSealed, Wart.While, Wart.Return, Wart.ExplicitImplicitTypes, Wart.Enumeration, Wart.FinalVal,
  Wart.TryPartial, Wart.TraversableOps, Wart.OptionPartial, ContribWart.SomeApply
)

wartremoverWarnings ++= wartremover.Warts.allBut(
  Wart.Nothing, Wart.DefaultArguments, Wart.Throw, Wart.MutableDataStructures, Wart.NonUnitStatements, Wart.Overloading,
  Wart.Option2Iterable, Wart.ImplicitConversion, Wart.ImplicitParameter, Wart.Recursion,
  Wart.Any, Wart.Equals, // Too many warnings because of spark's Row
  Wart.AsInstanceOf, // Too many warnings because of bad DI practices
  Wart.ArrayEquals // Too many warnings because we're using byte arrays in Spark
)


val playWsStandaloneVersion = "2.0.0-M6"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"       % "10.1.7",
  "com.typesafe.akka" %% "akka-stream"     % "2.5.19",
  "com.typesafe.play" %% "play-json"       % "2.6.13",
  "org.typelevel"     %% "cats-core"       % "1.5.0",
  "org.slf4j"          % "slf4j-api"       % "1.7.25",
  "ch.qos.logback"     % "logback-classic" % "1.2.3",
  "org.scalatest"     %% "scalatest"       % "3.0.5"   % Test
)