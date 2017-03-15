package org.kitsuneninetails.paidytest.server

import akka.actor.Actor
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._


object CurrentScores {
    case class SetScores(key: String,
                         scores: Vector[Double])
    case class GetNewScores(key:String,
                            score: Double)
}

class CurrentScores extends Actor {
    type ScoreMap = Map[String, Vector[Double]]

    implicit val timeout = Timeout(5.seconds)
    implicit val dispatcher = context.dispatcher

    import CurrentScores._

    def myReceive(csMap: ScoreMap = Map()): Receive = {
        case SetScores(key, scores) => Future(context.become(myReceive(csMap + (key -> (scores take 9))))) pipeTo sender()
        case GetNewScores(key, score) => Future(score +: csMap.getOrElse(key, Vector())) pipeTo sender()
    }
    override def receive(): Receive = myReceive(Map())

}
