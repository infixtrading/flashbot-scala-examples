This page shows how to configure a market data database, fill it up with live and historical data using Flashbot,
and how to view that data in two ways: programmatically with `FlashbotClient` and interactively with Grafana.

All code snippets are in Scala, as (apart from syntax) the Java version is nearly identical.
Additionally, source code for the example in this tutorial is published for both Java and Scala.

**Contents**
1. [Setup the database]()
2. [Collect market data]()
    1. [Configuration]()
    2. [Start a DataServer]()
3. [Poll market data with FlashbotClient]()
4. [Explore the market data dashboard]()
    1. [Start a TradingEngine]()
    2. [Setup Grafana]()
    3. [Open the dashboard]()

*The source code for this tutorial can be found at:*
* Java: [flashbot-market-data-dashboard](https://github.com/infixtrading/flashbot-java-examples/tree/master/flashbot-market-data-dashboard)
* Scala: [flashbot-market-data-dashboard](https://github.com/infixtrading/flashbot-scala-examples/tree/master/flashbot-market-data-dashboard)

****

The structure of the project in this example is the same as the projects in the [Project Setup (Java)]()
and [Project Setup (Scala)]() tutorial. The only difference is that the name of this project is "Flashbot Market Data Dashboard". Checkout the [build.sbt]() file as a reference.

### 1. Setup the database
Flashbot always saves market data to a SQL database. This step is optional during development because
an *in-memory* embedded H2 database is setup by default. This means that all data will be lost once 
the program exits. While this is useful in development, we'll need to data to disk in most cases.

Flashbot includes two different database configurations: `h2` and `postgres`.
The default config is `h2`, but you can change this by setting the `flashbot.db` property in
src/main/resources/application.conf:
```
flashbot {
  db = "postgres"
}
```

By default Flashbot expects the PostgreSQL database to be on localhost:5432 with the database name
"flashbot". Username and password are also "flashbot"/"flashbot". These settings are configurable
with the `dbHost`, `dbPort`, `dbName`, `dbUser`, and `dbPass` properties. 
These defaults are all listed near the bottom of the [reference.conf](https://github.com/infixtrading/flashbot/blob/master/modules/server/src/main/resources/reference.conf).

You can also create your own database configurations in your application.conf similar to the included ones. Then you can enable one of them by setting the `flashbot.db` setting to their property key.

### 2. Collect market data

#### i. Configuration

Our goal is to acquire the following data sets for the both the BTC-USD and ETH-USD products on Coinbase:
* Order books (real-time feed)
* Trades (historical AND real-time feed)
* 1-min price data (historical)

The first thing we need to do is add the `flashbot.sources` and `flashbot.ingest` properties to the configuration file (src/main/resources/application.conf). Here's what the additional configs should look like in order to ingest data described above:

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
    #   - "coinbase/eth_usd/trades"
    enabled = ["coinbase/*/book", "coinbase/*/trades"]

    # Enables historical data backfills for the following data paths:
    #   - "coinbase/btc_usd/candles_1m"
    #   - "coinbase/btc_usd/trades"
    #   - "coinbase/eth_usd/candles_1m"
    #   - "coinbase/eth_usd/trades"
    backfill = ["coinbase/*/candles_1m", "coinbase/*/trades"]

    # Declares that we're not interested in 1-min price data, trade data, and 
    # order book data that's older than 365 days, 90 days, and 1 hour, respectively.
    # Flashbot's data backfill process will not request data outside of those time
    # ranges, and will also actively delete data that falls out of it's retention 
    # period.
    retention = [
      ["*/*/candles_1m", "365d"],
      ["*/*/trades", "90d"],
      ["*/*/book", "1h"]
    ]
  }
  ...
}
```

The above works because Coinbase is a built-in data source. If you implement your 
own data source, or use a 3rd party one, the same config pattern should work as 
long as the data source supports the markets and data types we specify. 
Flashbot will complain they don't.

This final config file should look like [this]().

#### ii. Starting a DataServer

The next step is to start a `DataServer` so that it can perform the actual data ingest 
using the config above. It only takes a few lines of code!

Create a new file (src/main/scala/MarketDataServer.scala) which will handle all data collection.
```scala
import akka.actor.ActorSystem
import flashbot.config.FlashbotConfig
import flashbot.engine.DataServer

object MarketDataServer extends App {
  // Load config from src/main/resources/application.conf
  val config = FlashbotConfig.load()

  // Create the actor system and data server
  val system = ActorSystem(config.systemName, config.conf)
  val dataServer = system.actorOf(DataServer.props(config))
}
```

Note how we created a `DataServer` actor in the same way that we created a
`TradingEngine` in the project setup tutorial. The only difference here is that
we don't need to assign a unique name in each data server's props.

We'll run this in a separate process, so that it can collect data in the background
while we interact with it in various ways.

Run `MarketDataServer` using an IDE or open a separate command line terminal and run it with sbt:
```bash
$ sbt "runMain MarketDataServer"
TODO: output
```

### 3. Poll market data with FlashbotClient

Now we are collecting data, but how do we inspect it? We'll start by requesting
some data manually via `FlashbotClient` in a separate process. Later, we'll simplify
this by skipping `FlashbotClient` part and using a dashboard to interactively 
explore our data instead.

First, we'll need to create a `TradingEngine` for the client to connect to. This trading engine
will form a local cluster with the data server. The data server is currently bound
(by default) to port 2551 on localhost. In order to start a trading engine on the
same machine, it needs to be bound to another port (say, 2552), or else it will 
fail to start due to the default 2551 port being already taken.

To do this, create a separate config file for the engine's actor system to use.
Name it engine.conf and `include` the original application.conf to share those settings.
The new engine.conf will override only one property:

```
# Uses the application.conf config as a base for this one
include "application"

# Overrides the port which Akka Cluster binds to
akka.port = 2552
```

Create a new file at src/main/scala/PollTrades.scala:
```scala
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import flashbot.client.FlashbotClient
import flashbot.config.FlashbotConfig
import flashbot.core.Trade
import flashbot.engine.TradingEngine

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object PollTrades extends App {
  // A. Load config from src/main/resources/engine.conf
  val config = FlashbotConfig.load("engine")

  // B. Create the actor system and trading engine
  implicit val system = ActorSystem(config.systemName, config.conf)
  implicit val materializer = ActorMaterializer()
  val engine = system.actorOf(TradingEngine.props("trade-poller", config))

  // C. Create a FlashbotClient, link it to the engine
  val client = new FlashbotClient(engine)

  // D. Poll and print trades for 15 seconds.
  val donePolling: Future[Done] = 
    client.pollingMarketData[Trade]("coinbase/btc_usd/trades") 
      .takeWithin(15 seconds) 
      .runForeach(println)
  
  // E. Wait for polling to complete, then shutdown the system and exit program.
  val term = Await.ready(for {
    _ <- donePolling
    terminated <- system.terminate()
  } yield terminated, 20 seconds)
  System.exit(if (term.value.get.isSuccess) 0 else 1)
}
```

Let's review each step here:

##### A. Load engine.conf
This creates a `FlashbotConfig` that is based off of engine.conf, which has
identical properties to application.conf except for the `akka.port`. An Akka
system created with this config will bind to 2552 instead of the default 2551.

##### B. Create the actor system, materializer, and engine
Since we'll be working with Akka streams, we'll need an implicit `ActorMaterializer`
in scope in order to run operations such as `runForeach`, as we do in step D. If
you don't know what this is, it's safe to just copy/paste not worry about it yet.

Upon creation, the engine automatically forms a cluster with the `DataServer`
that is already running in another process.

##### C. Create the client
As before, create a new `FlashbotClient` and link it to the engine. What's
different this time though, is the engine is in a cluster with a data server.
That means that this client can now access live and historical data streams that
are being collected from the data server in another process.

##### D. Poll and print trades
Use the `client.pollingMarketData` method to get an Akka streams `Source` of 
live trades. The `takeWithin` method closes this stream 15 seconds after
materialization. And the `runForeach` method actually runs (materializes) the
stream and prints every trade that comes through.

##### E. Exit the system after polling
`donePolling` is a `Future` that completes after 15 seconds of polling trades.
After that is done, we'll want to shutdown the actor system (which returns another)
`Future`, and after that, we can exit the program. The `Await.ready(for { ... } yield ...)`
syntax is a convenient way of chaining those Futures together.

### 4. Explore the market data dashboard
Now we know how to query data from our market data cluster via code. But that's
not always very convenient. Most of the time, we just want to explore the data
visually. Enter Grafana.

Grafana is a free application for building dashboards of real-time data. 
While using Grafana is not a requirement for Flashbot, it's recommended because 
it's by far the easiest way to get visibility into your data and the performance 
of strategies.

#### i. Start a TradingEngine
The Grafana dashboard will build graphs for data that it receives from a trading engine.
So all we have to do is start a `TradingEngine` in a continuously running process,
just like we did in the previous step. The only difference being the 
`flashbot.grafana.data-source-port` config needs to be set to an open port. 
This tells the engine to create an embedded Grafana data source server at that port.
We'll use 3002 in this example.

Create a config file at src/main/resources/dashboard.conf:
```
import "application"
akka.port = 2552
flashbot.grafana.data-source-port = 3002
```

And use that config to start a long running `TradingEngine` in a new file 
(src/main/scala/MarketDataDashboard.scala):
```scala
```

#### ii. Setup Grafana


##### Step 1 - Install Grafana
[Install Grafana](http://docs.grafana.org/installation/) on your system. It should be accessible via web browser at [http://localhost:3000](http://localhost:3000)

Optionally, you can change the theme to "Light" in Configuration > Preferences.

##### Step 2 - Install the JSON plugin
[Install the JSON plugin](https://grafana.com/plugins/simpod-json-datasource) by simpod. On unix based systems, the command to do this is:
```bash
$ grafana-cli plugins install simpod-json-datasource
```

After installing the plugin, restart Grafana.

##### Step 3 - Configure the Flashbot data source
In Grafana, go to Configuration > Data Sources ([http://localhost:3000/datasources](http://localhost:3000/datasources))

1. Click the "Add data source" button in the top right.

2. Select the "JSON" data source.

3. Use the following values for the form. Leave all others with their default value.
    * **Name:** Flashbot
    * **URL:** http://localhost:3002
    * **Access:** Server

4. Click "Save & Test". This should connect successfully to our trading engine. 
If you get "HTTP Error Bad Gateway", double check that the trading engine is 
running with the `flashbot.grafana.data-source-port = 3002` property.


#### iii. Open the dashboard
Once Grafana was started, the engine immediately created a new Grafana folder
named "Flashbot" that will contain all dashboards managed by Flashbot. It also
created a dashboard for market data in that folder. Select it by going to the
Grafana main page ([http://localhost:3000](http://localhost:3000)), clicking on
the "Home" dropdown in the top left corner, and selecting the "Market Data" 
dashboard from the "Flashbot" folder.

This should display the market data that you've collected so far. It should look like this:

TODO: Screenshot

***

### Next 
Now we have a market data dashboard that is created and managed by Flashbot.
In the next section, we'll use other Grafana dashboard types to run backtests.
