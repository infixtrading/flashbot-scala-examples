import akka.actor.ActorSystem
import flashbot.config.FlashbotConfig
import flashbot.engine.TradingEngine

object MarketDataDashboard extends App {
  // Load config from src/main/resources/dashboard.conf
  val config = FlashbotConfig.load("dashboard")

  // Create the actor system and trading engine
  val system = ActorSystem(config.systemName, config.conf)
  val engine = system.actorOf(TradingEngine.props("dashboard-engine", config))
}