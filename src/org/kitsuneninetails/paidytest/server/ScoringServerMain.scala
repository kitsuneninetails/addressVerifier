package org.kitsuneninetails.paidytest.server

import scala.io.StdIn

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.settings.ServerSettings

import com.paidy.authorizations.actors.AddressFraudProbabilityScorer

import org.kitsuneninetails.paidytest.address_scorer.{AddressScorer, InMemoryScoreStore}

object ScoringServerMain extends App {
    override def main(args: Array[String]): Unit = {
        val system = ActorSystem("webserver-actors")
        val scorer = system.actorOf(Props[AddressFraudProbabilityScorer], name="scorer")
        val webServer = system.actorOf(Props[AddressScorerWebServer], name="webserver")
        val addressScoreStorage = new InMemoryScoreStore()
        val addressAverager = new AddressScorer(addressScoreStorage)
        val webServerFuture = webServer ? start("localhost", 8080)
        StdIn.readLine()
        scorer.sdf
        webServer.stop(webServerFuture)

    }
}
