# Flashbot Starter Project - Scala
This is the source code for the [Flashbot Project Setup (Scala)](https://github.com/infixtrading/flashbot/wiki/Project-Setup-(Scala)) tutorial.

The Java version of this project can be found at [Flashbot Starter Project - Java](https://github.com/infixtrading/flashbot-java-examples/tree/master/flashbot-starter-project)

***

This page shows how to setup Flashbot in a brand new Scala project.

Looking for Java instructions? Go to [Project Setup (Java)](https://github.com/infixtrading/flashbot/wiki/Project-Setup-(Java))

**Contents**
1. [Create an SBT project](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#1-collect-market-data)
2. [Configuration](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#3-run-backtests-using-the-dashboard)
3. [Pinging a simple TradingEngine](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#3-run-backtests-using-the-dashboard)

*The source code for this tutorial can be found at:*
* Java: [flashbot-starter-project](https://github.com/infixtrading/flashbot-java-examples/tree/master/flashbot-starter-project)
* Scala: [flashbot-starter-project](https://github.com/infixtrading/flashbot-scala-examples/tree/master/flashbot-starter-project)

****

### 1. Create an SBT project
Create a new folder named "flashbot-starter-project" and navigate to it. 
Create a build.sbt file in it that looks like this:
```sbtshell
name := "Flashbot Starter Project"

// Adds the Flashbot repository and library dependencies
resolvers += Resolver.bintrayRepo("infixtrading", "flashbot")
libraryDependencies ++= Seq(
    "com.infixtrading" %% "flashbot-client" % "0.1.0",
    "com.infixtrading" %% "flashbot-server" % "0.1.0"
)

// Prevents "sbt run" from hanging when the program exits
fork in run := true
```

This creates a new SBT Scala project that includes Flashbot as a dependency. 
This also automatically pulls in Akka as a transitive dependency, which we'll use in our Main class.

Additionally, create two empty directories:
* src/main/resources - Config files go here
* src/main/scala - Source files go here

### 2. Configuration
Flashbot (and Akka) use [Typesafe Config](https://github.com/lightbend/config) for configuration. 
Create a file (src/main/resources/application.conf) and add the following lines:
```
flashbot {
  engine-root = "target/flashbot/engines"
}

akka {
  loglevel = "WARNING"
}
```

This configures Flashbot to use the target/flashbot/engines directory 
for all TradingEngine persistence. This is where all bot state is saved. 
Note that this is the data storage for the TradingEngine itself, which is separate
from the SQL database (PostgreSQL or H2) that we'll be using to store market data. 
Check out the [reference config](https://github.com/infixtrading/flashbot/blob/master/modules/server/src/main/resources/reference.conf) 
to see all the defaults.

### 3. Pinging a simple TradingEngine
Create a new Scala file (src/main/scala/PingEngine.scala) that will hold our main app. In it, we'll create a new actor system named "example-system" and a TradingEngine actor with the default props. Then we'll ping the engine to ensure that is started properly. Here's what MarketDataDashboard.scala should look like:

```scala
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import flashbot.client.FlashbotClient
import flashbot.config.FlashbotConfig
import flashbot.engine.TradingEngine

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object PingEngine extends App {
  // A. Load config
  val config = FlashbotConfig.load()

  // B. Create the actor system which will contain the TradingEngine
  implicit val system = ActorSystem(config.systemName, config.conf)

  // C. Now, create the TradingEngine itself with the name "example-engine".
  val engine = system.actorOf(TradingEngine.props("example-engine", config))

  // D. Ping the engine with FlashbotClient.
  val client = new FlashbotClient(engine)
  val pong = client.ping()
  println(s"Engine started at: ${pong.startedAt}")

  // E. Shutdown the system and exit the program.
  val term = Await.ready(system.terminate(), 5 seconds)
  System.exit(if (term.value.get.isSuccess) 0 else 1)
}
```

Let's walk through this file step by step.

##### A. Load the config
All Flashbot projects begin by loading the config. This merges your application
specific settings with the defaults defined in [reference.conf](https://github.com/infixtrading/flashbot/blob/master/modules/server/src/main/resources/reference.conf).
If `load()` is called with no arguments, Flashbot looks for the src/main/references/application.conf file.
You can specify a different config file to use by passing the file name (without the ".conf" suffix):
```scala
// To load config from the src/main/resources/my-app.conf file.
FlashbotConfig.load("my-app")
```

##### B. Create the actor system
Akka is a requirement for running Flashbot. To get started, we need to create an
actor system. The first argument is the system name, which should come from the
Flashbot config. This can be set via the "flashbot.systemName" config property.

The second argument is the Akka configuration. This needs to come from the
`FlashbotConfig` as well, because Flashbot configures overrides several Akka
defaults. You can see all of Flashbot's Akka overrides in the "flashbot.akka" and
"flashbot.akka-cluster" properties in [reference.conf](https://github.com/infixtrading/flashbot/blob/master/modules/server/src/main/resources/reference.conf).

##### C. Create the TradingEngine
Now we can create a TradingEngine that lives in the actor system. A `TradingEngine`
is an Akka actor with a name that should be unique to it within the system. Actors
are created in Akka with the `system.actorOf` method with the `Props` of the desired
actor as the argument. Here, we use the helper method `TradingEngine.props()` to create
the props for a trading engine, given a custom name and the Flashbot config.

##### D. Ping the engine with FlashbotClient
The `FlashbotClient` is used to send queries and commands to a given engine. We can
check the engine for life signs by pinging it.

Note that, for convenience, `ping()` is a blocking call. However, all client methods 
have a non-blocking variant that returns a `Future`. In this case, that would be: 
```scala
val pongFut: Future[Pong] = client.pingAsync()
```

##### E. Shutdown the system and exit the program
The act of creating an `ActorSystem` as we just did will stop the program from
exiting, because the system is still running. Let's call `system.terminate()` and
wait for the system to finish terminating before exiting the JVM program itself.
This allows Flashbot to gracefully shutdown it's operations on exit.

*Now we can run the program with an IDE or `sbt run`:*

```bash
$ sbt run
[info] ... logging ...
[info] Engine started at: 2019-01-17T23:32:00.533Z
[success] Total time: 5 s, completed Jan 17, 2019 5:32:01 PM
```

***

### Next
In the [next tutorial](https://github.com/infixtrading/flashbot/wiki/Market-Data-Dashboard)
we will show how to ingest data and display it on a Grafana dashboard.


