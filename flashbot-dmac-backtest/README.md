# Flashbot DMAC Backtest - Scala
This is the source code for the [Backtesting a Built In Strategy]() tutorial.

The Java version of this project can be found at [Flashbot DMAC Backtest - Java](https://github.com/infixtrading/flashbot-java-examples/tree/master/flashbot-dmac-backtest)

***

This tutorial shows how to run the "Hello World" of algorithmic trading in Flashbot: 
Backtesting the Dual Moving Average Crossover strategy. First, we'll demonstrate how to run 
backtests programmatically using the `FlashbotClient`. Then, we'll use a Grafana dashboard to run 
backtests interactively.

**Contents**
1. [Create an SBT project]()
2. [Collect Market Data]()
3. [Review the DMAC strategy source code]()
4. [Backtests with FlashbotClient]()
5. [Backtests through the dashboard]()

*The source code for this tutorial can be found at:*
* Java: [flashbot-dmac-backtest](https://github.com/infixtrading/flashbot-java-examples/tree/master/flashbot-dmac-backtest)
* Scala: [flashbot-dmac-backtest](https://github.com/infixtrading/flashbot-scala-examples/tree/master/flashbot-dmac-backtest)

****

### 1. Create an SBT project
Create a new folder named "flashbot-dmac-backtest" and navigate to it. 
Create a build.sbt file in it that looks like this:
```sbtshell
name := "Flashbot DMAC Backtest"

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

Additionally, create two empty directories:
* src/main/resources - Config files go here
* src/main/scala - Source files go here


### 2. Collect Market Data
The market data collection aspect of this tutorial is identical to that in the
[Market Data Dashboard]() tutorial. Follow the instructions there to:
1. [Setup the PostgreSQL database]()
2. [Configure data ingest in application.conf]()
3. [Start the market data server]()

You should now have the following files:

*src/main/resources/application.conf*
```
flashbot {
  engine-root = "target/flashbot/engines"
  db = "postgres"
  
  sources {
    coinbase.sources = ["btc_usd", "eth_usd"]
  }

  ingest {
    enabled = ["coinbase/*/book", "coinbase/*/trades"]
    backfill = ["coinbase/*/candles_1m", "coinbase/*/trades"]
    retention = [
      ["*/*/candles_1m", "365d"],
      ["*/*/trades", "90d"],
      ["*/*/book", "1h"]
    ]
  }
}
```

*src/main/scala/MarketDataServer.scala*
```scala
import akka.actor.ActorSystem
import flashbot.config.FlashbotConfig
import flashbot.engine.DataServer

object MarketDataServer extends App {

  // Load config from src/main/resources/application.conf
  val config = FlashbotConfig.load()

  // Create the actor system and data server
  implicit val system = ActorSystem(config.systemName, config.conf)
  val dataServer = system.actorOf(DataServer.props(config))
}
```

Now you can start the market data server to begin collecting data. Run the file with an IDE or
with SBT in a separate command line terminal:
```bash
$ sbt "runMain MarketDataServer"
```

### 3. Review the DMAC strategy source code
The source code of the strategy can be found [here](). You don't need to understand everything 
in that file yet, as we haven't learned how to write strategies yet! But it helps to have a passing
familiarity with the code that we'll be testing.

The most important thing to take away from the code is the shape of the `Params` inner class.
Every strategy must define an inner class named `Params`, as well as a `Json => Params` decoder.
As backtesters, we will be trying out various values for each of those parameters to find the
combination with the best returns.

### 4. Backtests with FlashbotClient
Before we use the dashboard to run backtests, let's see what's happening behind the scenes by
first running one manually using `FlashbotClient`.

Create a new config file (src/main/scala/client-backtest.conf):
```
import "application"
akka.port = 2552
```

And a new Scala file which will run the backtest (src/main/scala/ClientBacktest.scala):

```scala
import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import flashbot.client.FlashbotClient
import flashbot.config.FlashbotConfig
import flashbot.engine.TradingEngine
import flashbot.models.core.Candle
import flashbot.report.Report
import io.circe.Json
import io.circe.syntax._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object ClientBacktest extends App {
  // Load config from src/main/resources/engine.conf
  val config = FlashbotConfig.load("client-backtest")

  // Create the actor system and trading engine
  implicit val system = ActorSystem(config.systemName, config.conf)
  implicit val materializer = ActorMaterializer()
  val engine = system.actorOf(TradingEngine.props("client-backtest-engine", config))

  // Create a FlashbotClient, link it to the engine
  val client = new FlashbotClient(engine)

  // Create a Json object that matches the shape of flashbot.strategies.DMAC#Params
  val params = Json.obj(
    "exchange" -> "coinbase".asJson,
    "product" -> "btc_usd".asJson,
    "data_type" -> "candles_1m".asJson,
    "short_sma" -> 7.asJson,
    "long_sma" -> 14.asJson,
    "stop_loss" -> .05.asJson,
    "take_profit" -> .05.asJson
  )

  // Start with 1 BTC and $500 USD in our portfolio.
  val initialPortfolio = Map(
    "btc" -> 1.0,
    "usd" -> 5000
  )

  // Run the backtest and get back a Report.
  val report: Report = client.backtest("dmac", params, initialPortfolio, 1 day)

  // The portfolio equity of the strategy is available in the "equity" time series
  // as 1-day wide candles, since that was the `interval` we supplied to the backtest.
  val equityByDay: Vector[Candle] = report.timeSeries("equity")

  // Calculate daily returns
  var dailyReturns: Vector[Double] = equityByDay.zipWithIndex.map {
    case (_, 0) => 1.0
    case (equity, i) => equity.close / equityByDay(i - 0).close
  }

  // Print daily equity and returns
  println("Date\tEquity\tReturns")
  for ((equity, returns) <- equityByDay zip dailyReturns) {
    val date = Instant.ofEpochMilli(equity.micros / 1000)
    println(s"$date\t$equity\t$returns")
  }

  // Exit the system
  val term = Await.ready(system.terminate() , 5 seconds)
  System.exit(if (term.value.get.isSuccess) 0 else 1)
}
```

This runs a backtest on the "dmac" strategy with a set of params and an initial portfolio. The
time range that the backtest should run in is not specified, which means the backfill will be run
on the maximum time range for which the data exists. Since we specified a 1-year data retention
policy for the dataset, the time range of the backtest will be no longer than 1 year. Less, if the
data backfill hasn't finished yet.

One thing to note is that the `data_type` parameter is set to "candles_1m", while the `interval`
of the backtest is set to `1 day`. What this means is that the backtest will be run on 1-minute
wide price candles (OHLC). That is the actual market data that the strategy is basing it's trading
off of. But the time series of the genrated report will be aggregated into 1-day wide candles.

Let's run the backtest now by running `ClientBacktest` with an IDE or an SBT:
```bash
$ sbt "runMain ClientBacktest"
TODO: Output
```


### 2. Backtests through the dashboard
Ensure Grafana is setup ([instructions](https://github.com/infixtrading/flashbot/wiki/Project-Setup-(Scala)#7-open-the-dashboard-in-grafana)).

Open Grafana in a web browser ([http://localhost:3000] by default) and select the "DMAC Backtest" 
dashboard in the top left dropdown. This dashboard is automatically created because Flashbot 
creates a backtest dashboard for all configured strategies, including the built-in ones like DMAC.

#### i. Backtest on candles
The DMAC strategy can run on either candle data or trade data. Let's start with 1-min candles. 
Set the time range (top right of the dashboard) to be one year and update the options at the top 
of the page to use the Coinbase exchange and "candles_1m" data type. This will immediately show 
the results of the backtest. It should look something like this:

todo: screenshot

This uses the default strategy parameters of 7 day short SMA and 30 day long SMA but those can also 
be updated. Try to change those parameters and see how that affects the strategy's performance.

#### ii. Backtest on trades
Change the dashboard time range to 3 months and the data type option to "trades".

You'll see that the performance of the strategy is slightly different than running on candles. 
This happens because when the strategy runs on trades, it is able to react to the market immediately. 
When running on 1-min candles, we're only able to react to prices once per minute.

### Next
In the [next section](https://github.com/infixtrading/flashbot/wiki/Tutorial:-Market-Making-Bot), 
we'll learn how to create custom strategies and backtest them in a similar way.
