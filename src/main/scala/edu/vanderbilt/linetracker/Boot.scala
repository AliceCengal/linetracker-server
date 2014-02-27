package edu.vanderbilt.linetracker

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import scala.io.Source

object Boot extends App {

  val serverConfig = readServerConfig()

  println("Successfully read the config file.")
  println("Address:               " + serverConfig.address)
  println("Website resource path: " + serverConfig.websiteResourcePath)

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our service actor
  val service = system.actorOf(Props(classOf[MyServiceActor], serverConfig), "demo-service")

  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ! Http.Bind(service, interface = serverConfig.address, port = 8080)

  println("Hit any key to exit.")
  val result = readLine()
  system.shutdown()

  def readServerConfig(): ServerConfig = {
    val configFile = getClass.getResourceAsStream("/data/server_info.txt")
    val lines = Source.fromInputStream(configFile).getLines()
    ServerConfig(lines.next(), lines.next())
  }

}
