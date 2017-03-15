package org.kitsuneninetails.paidytest.server

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
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
import scala.io.StdIn

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
object Main extends App with JsonSupport {
    override def main(args: Array[String]): Unit = {
        implicit val timeout = Timeout(5 seconds)
        implicit val system = ActorSystem("main-actors")
        implicit val materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher

        val scoreHandler = system.actorOf(Props[AddressFraudProbabilityScorer])
        val currentScores = system.actorOf(Props[CurrentScores])

        def passOrFail78And70(cs: Double,
                              ps: Vector[Double]): Boolean = {
            val avg: Vector[Double] => Double = l => l.sum / l.size
            (cs < 0.78) && (if (ps.size < 10) true else avg(ps) < 0.70)
        }

        val addressScore = {
            path("addressScore") {
                post {
                    entity(as[ScoreRequest]) { req =>

                        val addr = Address(req.line1, req.line2, req.city,
                            req.state, req.zipCode)
                        val scoreFuture = for {
                            f1 <- (scoreHandler ? ScoreAddress(addr)).mapTo[Double]
                            f2 <- (currentScores ? CurrentScores.GetNewScores(addr.hash(), f1)).mapTo[Vector[Double]]
                            _ <- currentScores ? CurrentScores.SetScores(addr.hash(), f2)
                        } yield passOrFail78And70(f1, f2)

                        complete(scoreFuture map {p => ScoreResponse(p)})
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
