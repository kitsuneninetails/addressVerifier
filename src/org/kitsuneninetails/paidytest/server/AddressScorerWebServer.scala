package org.kitsuneninetails.paidytest.server

import akka.AkkaException
import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.domain.Address
import org.kitsuneninetails.paidytest.address_scorer.PassFail
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

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

object AddressScorerWebServer {
    def props(actorSystem: ActorSystem): Props =
        Props(new AddressScorerWebServer(actorSystem))
}
class AddressScorerWebServer(actorSystem: ActorSystem)
    extends Actor
            with JsonSupport {

    implicit val timeout = Timeout(5.seconds)
    implicit val dispatcher = context.dispatcher
    implicit val materializer = ActorMaterializer.create(context)
    implicit val sys: ActorSystem = actorSystem

    val addressScore = {
        path("addressScore") {
            post {
                entity(as[ScoreRequest]) { req =>
                    val addr = Address(req.line1, req.line2, req.city,
                                       req.state, req.zipCode)
                    val passFail =
                        Await.result[Boolean](
                            (context.actorSelection("../scoreMediator") ? PassFail(addr)).mapTo[Boolean],
                            5 seconds)
                    complete(ScoreResponse(passFail))
                }
            }
        }
    }

    override def receive = {
        case Start(server, port) => start(server, port) pipeTo sender()
        case Stop(servFuture) => stop(servFuture)
    }

    def start(server: String, port: Int): Future[Http.ServerBinding] =
        Http().bindAndHandle(RouteResult.route2HandlerFlow(addressScore),
                             server, port)

    def stop(bindingFuture: Future[Http.ServerBinding]): Unit=
        bindingFuture.flatMap(_.unbind())

}
