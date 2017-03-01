package org.kitsuneninetails.paidytest.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.pattern.ask
import akka.util.Timeout
import org.kitsuneninetails.paidytest.address_scorer.{AddressMediator, InMemoryScoreStore}

import scala.concurrent.duration._
import scala.io.StdIn

object ScoringServerMain extends App {
    override def main(args: Array[String]): Unit = {
        implicit val timeout = Timeout(5.seconds)

        val addressScoreStorage = new InMemoryScoreStore()
        val system = ActorSystem("webserver-actors")

        val webServer = system.actorOf(AddressScorerWebServer.props(system), name="webserver")
        val scoreMediator = system.actorOf(AddressMediator.props(addressScoreStorage), name="scoreMediator")

        val webServerFuture = (webServer ? Start("localhost", 8080)).mapTo[Http.ServerBinding]
        println("Press ENTER to stop...")
        StdIn.readLine()
        webServer ! Stop(webServerFuture)
        system.terminate()
    }
}
