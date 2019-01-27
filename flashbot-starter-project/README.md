# Flashbot Starter Scala Project
This is the source code for the [Flashbot Project Setup (Scala)](https://github.com/infixtrading/flashbot/wiki/Project-Setup-(Scala)) tutorial.

The Java version of this project can be found at [Flashbot Starter Java Project](https://github.com/infixtrading/flashbot-java-examples/tree/master/flashbot-starter-project)

***

This page shows how to setup Flashbot in a brand new Scala project.

Looking for Java instructions? Go to [Project Setup (Java)](https://github.com/infixtrading/flashbot/wiki/Project-Setup-(Java))

**Contents**
1. [Create an SBT project](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#1-collect-market-data)
2. [Add the Flashbot dependency](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#2-run-backtests-using-flashbotclient)
3. [Configuration](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#3-run-backtests-using-the-dashboard)
4. [Pinging a simple TradingEngine](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy#3-run-backtests-using-the-dashboard)

*The source code for this tutorial can be found at:*
* Java: [flashbot-starter-project](https://github.com/infixtrading/flashbot-java-examples/tree/master/flashbot-starter-project)
* Scala: [flashbot-starter-project](https://github.com/infixtrading/flashbot-scala-examples/tree/master/flashbot-starter-project)

****

### 1. Create an SBT project
Start a new SBT project. An easy way to do this is with the `scala-seed` Giter8 template, like this:
```bash
# Use the project name "Flashbot Starter Project". This initializes
# the project in a new directory named "flashbot-starter-project".
$ sbt new scala/scala-seed.g8
```

Ensure everything is working by navigating to the directory and running the project. 
This should print "hello" to the console.
```bash
$ cd flashbot-starter-project
$ sbt run
[info] ... a bunch of logging ...
[info] Running example.Hello
hello
[success] Total time: 1 s, completed Jan 16, 2019 12:59:58 PM
```

### 2. Add the Flashbot dependency
Edit your build.sbt file per the [installation instructions](https://github.com/infixtrading/flashbot#sbt) 
in the README. This includes adding the Flashbot repository as well as the `flashbot-client` and 
`flashbot-server` dependencies. This also automatically pulls in Akka as a transitive dependency, 
which we'll use in our Main class.

Additionally:
1. Add `fork in run := true` to your project settings. This allows `sbt run` to exit properly.
2. Remove the `organization` and `organizationName` settings.

The final build.sbt should look like [this](https://github.com/infixtrading/flashbot-scala-examples/blob/master/flashbot-starter-project/build.sbt).

### 3. Configuration
Flashbot (and Akka) use [Typesafe Config](https://github.com/lightbend/config) for configuration. 
Create a file named application.conf in src/main/resources and add the following lines:
```
flashbot {
  engine-root = "target/flashbot/engines"
}

akka {
  loglevel = "WARNING"
}
```

This configures Flashbot to use the <project_root>/target/flashbot/engines directory 
for all TradingEngine persistence. This is where all bot state is saved. 
Note that this is the data storage for the TradingEngine itself, which is separate
from the SQL database (PostgreSQL or H2) that we'll be using to store market data. 
Check out the [reference config](https://github.com/infixtrading/flashbot/blob/master/modules/server/src/main/resources/reference.conf) 
to see all the defaults.

### 4. Pinging a simple TradingEngine
First, if you used the Giter8 template, delete the `example` package that comes with it:
```bash
$ rm -rf src/main/scala/example
$ rm -rf src/test/scala/example
```

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


### 5. Check the engine for signs of life
Executing `sbt run` should result in the following output:

```bash
$ sbt run
[info] ... logging ...
[info] Engine started at: 2019-01-17T23:32:00.533Z
[success] Total time: 5 s, completed Jan 17, 2019 5:32:01 PM
```


Now that setup is out of the way, we'll see first-hand how easy it is to build a real-time market data dashboard (including historical data!) by letting Flashbot ingest data directly from an exchange.

Our goal will be to acquire the following data sets for the both the BTC-USD and ETH-USD products on Coinbase:
* Order books (real-time feed)
* Trades (historical AND real-time feed)
* 1-min price data (historical)

The main thing we need to do is add the `flashbot.sources` and `flashbot.ingest` properties to the configuration file (src/main/resources/application.conf). Here's what the additional configs should look like in order to ingest data described above:
```
flashbot {
  ...
  sources {
    # Declares the "btc_usd" and "eth_usd" pairs for the "coinbase" data source.
    coinbase.sources = ["btc_usd", "eth_usd"]
  }

  ingest {
    # Enables live-data ingest for the following data paths:
    #   - "coinbase/btc_usd/book"
    #   - "coinbase/btc_usd/trades"
    #   - "coinbase/eth_usd/book"
    #   - "coinbase/eth_usd/trades".
    enabled = ["coinbase/*/book", "coinbase/*/trades"]

    # Enables historical data backfills for the following data paths:
    #   - "coinbase/btc_usd/candles_1m"
    #   - "coinbase/btc_usd/trades"
    #   - "coinbase/eth_usd/candles_1m"
    #   - "coinbase/eth_usd/trades".
    backfill = ["coinbase/*/candles_1m", "coinbase/*/trades"]

    # Declares that we're not interested in 1-min price data, trade data, and order book data
    # that's older than 180 days, 90 days, and 7 days, respectively. Flashbot's data ingest
    # process will not request data outside of those time ranges, and will also actively delete
    # data that falls out of it's retention period.
    retention = [
      ["*/*/candles_1m", "180d"],
      ["*/*/trades", "90d"],
      ["*/*/book", "7d"]
    ]
  }
  ...
}
```

The above works because Coinbase is a built-in data source. If you implement your own data source, or use a 3rd party one, the same config pattern should work as long as the data source supports the markets and data types we specify. Flashbot will complain they don't.

The last step is to start a `DataServer` so that it can perform the actual data ingest based on the config we just created. It only takes one line of code! Go back to MarketDataDashboard.scala and add the following line right below `val system = ...`:
```scala
// Spin up the data server
val dataServer = system.actorOf(DataServer.props(config))
```

Now we have the necessary code that runs data ingest. But how do we inspect it? We'll start by requesting it through the `FlashbotClient`, which is hooked up to the `TradingEngine`. So to link everything together, let's connect the trading engine to the data server. Change the line where we create the trading engine from this:
```scala
val engine = system.actorOf(TradingEngine.props("example-engine", config))
```
to this:
```scala
val engine = system.actorOf(TradingEngine.props("example-engine", config, dataServer))
```

So far, we have declared what data we need in `application.conf`, created a `DataServer` that will get that data for us, connected it to our main `TradinEngine`, and connected to the `TradingEngine` with a `FlashbotClient`.

We will want to keep the program running so that the client can poll & print the market data as it's coming in from the data server. Right above the last three lines of `MarketDataDashboard.scala` where we exit the system, replace the `client.pong()` code with the following:
```scala
// Poll and print trades for 10 seconds.
val done = client.pollingMarketData[Trade]("coinbase/btc_usd/trades")
  .runForeach(println)
Await.ready(done, 10 seconds)
```

The source code of file in it's final state can be found [here](https://github.com/infixtrading/flashbot-scala-examples/blob/master/flashbot-starter-project/src/main/scala/MarketDataDashboard.scala):

At last, execute `sbt run` from the command line again. This program will print every live "btc_usd" trade that for 10 seconds. The output of the program should look like this:

```bash
$ sbt run
...
... todo: output
...
```

### 7. Open the dashboard in Grafana
If you haven't yet, [install Grafana](http://docs.grafana.org/installation/). This guide assumes that it's running on localhost:3000 with the default login params (admin/admin). Otherwise you can update any of the following config keys:
```
flashbot {
  grafana {
    host = "localhost"
    port = 3000
    username = "admin"
    password = "admin"
  }
}
```


Go to [http://localhost:3000](http://localhost:3000) and select the "Market Data" dashboard from the left main panel.

<screenshot>

### 8. Setup PostgreSQL database for persisting data (optional)
You may be wondering where the market data is being saved, since we haven't setup a database yet. Flashbot DataServers always save data to a SQL database and is configured by default to use an *in-memory* embedded H2 database. This means that all data will be lost once the program exits. While this is useful in development, we'll need to data to disk in most cases.

Flashbot includes two different database configurations: `h2` and `postgres`. The default config is `h2`, but you can change this by setting the `flashbot.db` property in application.conf:
```
flashbot {
  db = "postgres"
}
```

By default Flashbot expects the PostgreSQL database to be on localhost:5432 with the database name "flashbot". Username and password are also "flashbot"/"flashbot". These settings are configurable with the `dbHost`, `dbPort`, `dbName`, `dbUser`, and `dbPass` properties. These defaults are all listed near the bottom of the [reference.conf](https://github.com/infixtrading/flashbot/blob/master/modules/server/src/main/resources/reference.conf).

You can also create your own database configurations in your application.conf similar to the included ones. Then you can enable one of them by setting the `flashbot.db` setting to their property key.

### 9. Next steps

Now we have a market data dashboard that is created and managed by Flashbot. [In the next section](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Backtesting-a-Built-In-Strategy), we'll use other Grafana dashboard types to run backtests.