//import akka.Done
//import akka.actor.ActorSystem
//import akka.stream.ActorMaterializer
//import flashbot.client.FlashbotClient
//import flashbot.config.FlashbotConfig
//import flashbot.core.Trade
//import flashbot.engine.TradingEngine
//
//import scala.concurrent.{Await, Future}
//import scala.concurrent.duration._
//import scala.language.postfixOps
//
//object PollTrades extends App {
//  // Load config
//  val config = FlashbotConfig.load()
//
//  // Create the actor system which will contain the TradingEngine
//  implicit val system = ActorSystem(config.systemName, config.conf)
//
//  // Create the actor materializer, which needs to be in scope in order to
//  // consume market data streams from the engine.
//  implicit val materializer = ActorMaterializer()
//
//  // Now, create the TradingEngine itself with the name "example-engine".
//  val engine = system.actorOf(TradingEngine.props("example-engine"))
//
//  // Create a FlashbotClient, used to communicate with the engine.
//  val client = new FlashbotClient(engine)
//
//  // Poll live trades for 15 seconds.
//  val donePolling: Future[Done] =
//    client.pollingMarketData[Trade]("coinbase/btc_usd/trades")
//      .takeWithin(15 seconds)
//      .runForeach(println)
//
//  // Wait for polling to finish, then shutdown the system and exit the program.
//  val term = Await.ready(for {
//    _ <- donePolling
//    terminated <- system.terminate()
//  } yield terminated, 20 seconds)
//  System.exit(if (term.value.get.isSuccess) 0 else 1)
//}