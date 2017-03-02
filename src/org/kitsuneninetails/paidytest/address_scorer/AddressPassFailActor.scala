package org.kitsuneninetails.paidytest.address_scorer

import akka.actor.{Actor, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.domain.{Address, Score}

import scala.concurrent.Future
import scala.concurrent.duration._

final case class PassFail(addr: Address)

object AddressPassFailActor {
    def props(scoreStore: ScoreStorage): Props =
        Props(new AddressPassFailActor(scoreStore))
}

class AddressPassFailActor(val scoreStore: ScoreStorage) extends Actor {
    val scorer = context.actorOf(Props[AddressFraudProbabilityScorer], name="scorer")
    implicit val timeout = Timeout(5 seconds)
    implicit val dispatcher = context.dispatcher

    def score(addr: Address): Future[Score] = {
        (scorer ? ScoreAddress(addr)).mapTo[Double] map { result =>
            try {
                val avg = scoreStore.addAndAverage(addr.hash(), result)
                Score(avg < 0.70 && result < 0.78, result, avg)
            } catch {
                case ex: ScoreStorageInsufficientSamplesException =>
                    Score(result < 0.78, result, result)
            }
        }
    }

    override def receive = {
        case PassFail(addr) => score(addr) pipeTo sender()
    }
}
