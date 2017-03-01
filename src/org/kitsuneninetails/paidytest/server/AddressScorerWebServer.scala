package org.kitsuneninetails.paidytest.server

import akka.AkkaException
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.pattern.{ask, pipe}

import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.domain.Address
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import akka.http.scaladsl.settings.ServerSettings

import spray.json.DefaultJsonProtocol

final case class Start(server: String,
                       port: Int)
final case class Stop(serverFuture: Future[Http.ServerBinding])
final case class ScoreRequest(line1: String,
                              line2: String,
                              city: String,
                              state: String,
                              zipCode: String)
final case class ScoreResponse(success: Boolean, error: String = "")

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val requestFormat = jsonFormat5(ScoreRequest)
    implicit val responseFormat = jsonFormat2(ScoreResponse)
}

class AddressScorerWebServer()
    extends Actor
            with JsonSupport {
    val scorer: ActorRef = context.actorOf(Props[AddressFraudProbabilityScorer], "scorer")
    val passFail: ActorRef = context.actorOf(Props[AddressFraudProbabilityScorer], "scorer")
    val addressScore = {
        path("addressScore") {
            post {
                entity(as[ScoreRequest]) { req =>
                    val addr = Address(req.line1, req.line2, req.city,
                                       req.state, req.zipCode)
                    val score =(scorer ? ScoreAddress(addr)) flatMap _
                    var passFail =
                    println(s"${req.line1}, ${req.line2}, ${req.city}, " +
                            s"${req.state}, ${req.zipCode} == $score")
                    complete(ScoreResponse(score <= 75.0))
                }
            }
        }
    }

    override def receive = {
        case Start(server, port) => start(server, port) pipeTo sender()
        case Stop(servFuture) => stop(servFuture)
        case _ => throw new AkkaException("No messages supported")
    }

    def start(server: String, port: Int): Future[Http.ServerBinding] =
        Http().bindAndHandle(RouteResult.route2HandlerFlow(addressScore),
                             server, port)

    def stop(bindingFuture: Future[Http.ServerBinding]): Unit=
        bindingFuture.flatMap(_.unbind())

}
