package org.kitsuneninetails.paidytest.server

import java.util.concurrent.TimeoutException

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.domain.Address
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

final case class ScoreRequest(line1: String,
                              line2: String,
                              city: String,
                              state: String,
                              zipCode: String)
final case class ScoreResponse(success: Boolean)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val requestFormat = jsonFormat5(ScoreRequest)
    implicit val responseFormat = jsonFormat1(ScoreResponse)
}

case class PFReturn(passFail: Boolean, newScores: List[Double])

object Main extends App with JsonSupport {
    override def main(args: Array[String]): Unit = {
        implicit val timeout = Timeout(5 seconds)

        implicit val system = ActorSystem("main-actors")
        implicit val materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher

        val scoreHandler = system.actorOf(Props[AddressFraudProbabilityScorer])
        val currentScores = system.actorOf(Props[CurrentScores])

        def passOrFail(currentScore: Double,
                       pastScores: List[Double])
                      (sf: Double => Boolean)
                      (af: List[Double] => Boolean): PFReturn = {
            val newScores = (currentScore :: pastScores) take 10
            PFReturn(sf(currentScore) && af(newScores), newScores)
        }

        def passOrFail78And70(currentScore: Double,
                              pastScores: List[Double]): PFReturn = {
            def avg(l: List[Double]): Double = l.sum / l.size
            passOrFail(currentScore, pastScores) {_ < 0.78} {l => if (l.size < 10) true else avg(l) < 0.70}
        }

        val addressScore = {
            path("addressScore") {
                post {
                    entity(as[ScoreRequest]) { req =>

                        val addr = Address(req.line1, req.line2, req.city,
                            req.state, req.zipCode)
                        val scoreFuture = for {
                            f1 <- (scoreHandler ? ScoreAddress(addr)).mapTo[Double]
                            f2 <- (currentScores ? CurrentScores.GetScores(addr.hash())).mapTo[List[Double]]
                        } yield passOrFail78And70(f1, f2)

                        val scoreResult = Try(Await.result(scoreFuture, 5 seconds))
                        scoreResult match {
                            case Success(pfRet) =>
                                currentScores ! CurrentScores.SetScores(addr.hash(), pfRet.newScores)
                                complete(ScoreResponse(pfRet.passFail))
                            case Failure(e) =>
                                e match {
                                    case e: TimeoutException =>
                                        complete(HttpResponse(
                                            StatusCodes.InternalServerError,
                                            entity = "Request took longer than 5 seconds to complete"))
                                    case _ =>
                                        complete(HttpResponse(
                                            StatusCodes.InternalServerError, entity = e.getMessage))
                                }
                        }
                    }
                }
            }
        }

        val serverFuture = Http().bindAndHandle(
            Route.handlerFlow(addressScore), "localhost", 8080)

        println("Press ENTER to stop...")
        StdIn.readLine()

        serverFuture.flatMap(_.unbind())
        system.terminate()
    }

}
