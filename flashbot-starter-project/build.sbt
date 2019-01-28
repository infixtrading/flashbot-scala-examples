name := "Flashbot Starter Project"

// Adds the Flashbot repository and library dependencies
resolvers += Resolver.bintrayRepo("infixtrading", "flashbot")
libraryDependencies ++= Seq(
  "com.infixtrading" %% "flashbot-client" % "0.1.0",
  "com.infixtrading" %% "flashbot-server" % "0.1.0"
)

// Prevents "sbt run" from hanging when the program exits
fork in run := true