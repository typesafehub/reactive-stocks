package actors

import javax.inject._

import akka.actor._
import akka.event.LoggingReceive
import akka.stream.scaladsl.SourceQueueWithComplete
import com.google.inject.assistedinject.Assisted
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.json._

class UserActor @Inject()(@Assisted out: SourceQueueWithComplete[JsValue],
                          @Named("stocksActor") stocksActor: ActorRef,
                          configuration: Configuration) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    configureDefaultStocks()
  }

  override def postStop(): Unit = {
    out.complete()
  }

  def configureDefaultStocks(): Unit = {
    val defaultStocks = configuration.get[Seq[String]]("default.stocks")
    log.info(s"Creating user actor with default stocks $defaultStocks")

    for (stockSymbol <- defaultStocks) {
      stocksActor ! WatchStock(stockSymbol)
    }
  }

  override def receive: Receive = LoggingReceive {
    case StockUpdate(symbol, price) =>
      val stockUpdateMessage = Json.obj("type" -> "stockupdate", "symbol" -> symbol, "price" -> price.doubleValue())
      out.offer(stockUpdateMessage)

    case StockHistory(symbol, history) =>
      val numberSeq = history.map(h => Json.toJson[Double](h))
      val stockUpdateMessage = Json.obj("type" -> "stockhistory", "symbol" -> symbol, "history" -> numberSeq)
      out.offer(stockUpdateMessage)

    case json: JsValue =>
      // When the user types in a stock in the upper right corner, this is triggered
      val symbol = (json \ "symbol").as[String]
      stocksActor ! WatchStock(symbol)
  }
}

class UserParentActor @Inject()(childFactory: UserActor.Factory) extends Actor with InjectedActorSupport with ActorLogging {
  import UserParentActor._

  override def receive: Receive = LoggingReceive {
    case Create(id, out) =>
      val child: ActorRef = injectedChild(childFactory(out), s"userActor-$id")
      sender() ! child
  }
}

object UserParentActor {
  case class Create(id: String, out: SourceQueueWithComplete[JsValue])
}

object UserActor {
  trait Factory {
    // Corresponds to the @Assisted parameters defined in the constructor
    def apply(out: SourceQueueWithComplete[JsValue]): Actor
  }
}
