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