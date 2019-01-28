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
