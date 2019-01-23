import akka.actor.ActorSystem
import com.infixtrading.flashbot.client.FlashbotClient
import com.infixtrading.flashbot.core.FlashbotConfig
import com.infixtrading.flashbot.engine.TradingEngine

import scala.concurrent.Await
import scala.concurrent.duration._

object Engine extends App {
  // Load config
  val config = FlashbotConfig.load

  // Create the actor system and trading engine
  val system = ActorSystem("example-system", config.conf)
  val engine = system.actorOf(TradingEngine.props("example-engine"))

  // Create a FlashbotClient
  val client = new FlashbotClient(engine)

  // Ping the trading engine.
  // All client methods have an async version that return a future instead of blocking.
  // In this case, that would be: client.pingAsync() => Future[Pong]
  val pong = client.ping()

  // Log engine start time to stdout
  println(s"Engine started at: ${pong.startedAt}")

  // Gracefully shutdown the system and exit the program.
  val term = Await.ready(system.terminate(), 5.seconds)
  System.exit(if (term.value.get.isSuccess) 0 else 1)
}
