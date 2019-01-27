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