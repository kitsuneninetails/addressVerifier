package org.kitsuneninetails.paidytest.address_scorer

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.domain.Address

import scala.concurrent.Await
import scala.concurrent.duration._

final case class PassFail(addr: Address)

object AddressMediator {
    def props(scoreStore: ScoreStore): Props =
        Props(new AddressMediator(scoreStore))
}

class AddressMediator(val scoreStore: ScoreStore) extends Actor {
    def score(addr: Address, score: Double): Boolean =
        scoreStore.addScore(addr.hash(), score) < 0.70 && score < 0.78

    val scorer = context.actorOf(Props[AddressFraudProbabilityScorer], name="scorer")
    implicit val timeout = Timeout(5.seconds)
    implicit val dispatcher = context.dispatcher

    override def receive = {
        case PassFail(addr) =>
            sender !
                score(addr,
                      Await.result[Double](
                          (scorer ? ScoreAddress(addr)).mapTo[Double],
                          5 seconds))
    }
}
