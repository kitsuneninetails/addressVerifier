package org.kitsuneninetails.paidytest.server

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.Timeout

import com.paidy.domain.{Address, Score}

import org.kitsuneninetails.paidytest.address_scorer.{AddressPassFailActor, PassFail}

import spray.json.DefaultJsonProtocol
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}

final case class Start(server: String,
                       port: Int)
final case class Stop(serverFuture: Future[Http.ServerBinding])

final case class ScoreRequest(line1: String,
                              line2: String,
                              city: String,
                              state: String,
                              zipCode: String)
final case class ScoreResponse(success: Boolean, score: Double, average: Double)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val requestFormat = jsonFormat5(ScoreRequest)
    implicit val responseFormat = jsonFormat3(ScoreResponse)
}

class AddressScorerWebServer()
    extends Actor
            with JsonSupport {

    implicit val timeout = Timeout(5 seconds)
    implicit val dispatcher = context.dispatcher
    implicit val materializer = ActorMaterializer.create(context)
    implicit val sys: ActorSystem = ActorSystem("web-server")

    val scoreHandler = context.actorOf(Props[AddressPassFailActor], name="scoreHandler")

    val addressScore = {
        path("addressScore") {
            post {
                entity(as[ScoreRequest]) { req =>
                    try {
                        val addr = Address(req.line1, req.line2, req.city,
                                           req.state, req.zipCode)
                        val score = Await.result[Score](
                            ( scoreHandler ? PassFail(addr)).mapTo[Score], 5 seconds)
                        complete(ScoreResponse(score.pass, score.score, score.average))
                    } catch {
                        case t: TimeoutException =>
                            complete(HttpResponse(
                                StatusCodes.InternalServerError,
                                entity=s"API request timed out after 5 seconds"))
                        case t: Exception =>
                            complete(HttpResponse(
                                StatusCodes.InternalServerError, entity=t.getMessage))
                    }
                }
            }
        }
    }

    override def receive = {
        case Start(server, port) => start(server, port) pipeTo sender()
        case Stop(servFuture) => stop(servFuture)
    }

    def start(server: String, port: Int): Future[Http.ServerBinding] =
        Http().bindAndHandle(addressScore, server, port)

    def stop(bindingFuture: Future[Http.ServerBinding]): Unit= {
        bindingFuture.flatMap(_.unbind())
        sys.terminate()
    }

}
