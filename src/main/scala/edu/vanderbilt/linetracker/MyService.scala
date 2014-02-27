package edu.vanderbilt.linetracker

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.Props
import spray.routing._
import spray.http._
import MediaTypes._
import akka.util.Timeout
import akka.pattern.ask
import spray.http.HttpHeaders.RawHeader


// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)

}


// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

  import LinetrackerServer._

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val timeout = Timeout(5 seconds)
  val linetracker = actorRefFactory.actorOf(Props[LinetrackerServer], "linetracker")

  val myRoute =
    pathPrefix("linetracker") {
      pathPrefix("api") {
        path("index.html") {
          getFromResource("linetracker_index.txt")
        } ~
        path("alllines") {
          respondWithMediaType(`application/json`) {
            respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
              complete {
                import LinetrackerServer._
                (linetracker ? GetAllLines).mapTo[String]
              }
            }
          }
        } ~
        pathPrefix("line" / IntNumber) { id =>
          path("submittime" / IntNumber) { waitTime =>
            linetracker ! SubmitTime(id, waitTime)
            respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
              complete("OK")
            }
          } ~
          path("summary") {
            respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
              complete {
                (linetracker ? SummaryFor(id)).mapTo[String]
              }
            }

          } ~
          path("raw") {
            respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
              complete {
                (linetracker ? RawFor(id)).mapTo[String]
              }
            }

          }
        }
      } ~
      getFromResourceDirectory("website")
    }

}

