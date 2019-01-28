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
