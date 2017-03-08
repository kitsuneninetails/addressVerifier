package org.kitsuneninetails.paidytest.server

import akka.actor.Actor
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._


object CurrentScores {
    case class SetScores(key: String,
                         scores: List[Double])
    case class GetScores(key:String)
}

class CurrentScores extends Actor {
    type ScoreMap = Map[String, List[Double]]
    private var currentScoreMap: ScoreMap = Map()

    implicit val timeout = Timeout(5.seconds)
    implicit val dispatcher = context.dispatcher

    import CurrentScores._

    override def receive(): Receive = {
        case SetScores(key, scores) => currentScoreMap += (key -> scores)
        case GetScores(key) => Future(currentScoreMap.getOrElse(key, List())) pipeTo sender()
    }

}
