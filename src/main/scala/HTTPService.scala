import scala.concurrent.Future
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}

import scala.io.StdIn

case class ID(id: Long) extends AnyVal
trait Cache[ID] {
  def get(key: ID): Future[Option[String]]
  def put(key: ID, value: String): Future[Option[Unit]]
}

class CampaignsDataStore extends Cache[ID] with Actor with ActorLogging {
  private[this] val cache = collection.concurrent.TrieMap.empty[ID, String]
  override def get(key: ID) = Future { cache.get(key) }
  override def put(key: ID, value: String) = Future { store.put(key, value) }

  def receive = {
    case getID: ID => this.get(getID)
    case putID: (ID, String) => this.put(_, _)
  }

}

//val cache: Cache[ID] = ???

object RESTService extends App {

  implicit val system = ActorSystem("Restful-HTTP-API-V1")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  implicit val cache = system.actorOf(Props[Cache[ID]],"Data-store")
  val route = pathPrefix("ids"/ LongNumber) { id => {
      get {
        val item: Future[Option[_]] = Future { Some(ID(id)) }
        onSuccess(item) {
          case Some(identifier) => complete(s"$identifier")
          case None => complete("Not in DB")
        }
//        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
      } ~
     post {
       complete("")
     }
    }
  }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

}