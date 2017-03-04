package org.kitsuneninetails.paidytest.server

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.io.StdIn

object ScoringServerMain extends App {
    override def main(args: Array[String]): Unit = {
        implicit val timeout = Timeout(5 seconds)

        val system = ActorSystem("main-actors")

        val webServer = system.actorOf(Props[AddressScorerWebServer], name="webserver")

        val webServerFuture = (webServer ? Start("localhost", 8080)).mapTo[Http.ServerBinding]
        println("Press ENTER to stop...")
        StdIn.readLine()
        webServer ! Stop(webServerFuture)
        system.terminate()
    }
}
