package edu.vanderbilt.linetracker

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import scala.io.Source

object Boot extends App {

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our service actor
  val service = system.actorOf(Props[MyServiceActor], "demo-service")

  val serverIP = getServerIpAddress();

  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ! Http.Bind(service, interface = serverIP, port = 8080)

  println("Hit any key to exit.")
  val result = readLine()
  system.shutdown()

  def getServerIpAddress(): String = {
    val is = getClass.getResourceAsStream("/data/server_info.txt")
    Source.fromInputStream(is).getLines().next()
  }

}
