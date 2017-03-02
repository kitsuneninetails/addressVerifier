package org.kitsuneninetails.paidytest.address_scorer

import scala.collection.mutable.{HashMap => MutableHashMap, Queue => MutableQueue}

// A Rolling queue that will append up to a certain size and then drop
// older items as new ones get added in order to keep a maximum size.
class RollingQueue[A](maxSize: Int) extends MutableQueue[A] {
    def addOrRotate(value: A): RollingQueue.this.type = {
        this += value
        if (this.size > maxSize) dequeue()
        this
    }
}

class ScoreStorageInsufficientSamplesException extends Exception

// Keep a record of the last ten scores for each key.  Use an in-memory
// mutable map to simulate a DB
class LastTenInMemoryStorage extends ScoreStorage {
    type ScoreQueue = RollingQueue[Double]
    val scoreMap: MutableHashMap[String, ScoreQueue] =
        new MutableHashMap()

    // Return an average of the last ten scores for a key.  Throw an exception
    // if there aren't ten scores to assess
    override def addAndAverage(key: String, score: Double): Double = {
        val scoreQueue = scoreMap.getOrElseUpdate(key,
                                                  new ScoreQueue(10))
        // Keep non-functional part separate (this will change state as this
        // is an in-memory map simplifying some kind of DB or data store)
        // to help show that this is a change in the state of the scoreQueue.
        scoreQueue.addOrRotate(score)
        if (scoreQueue.size < 10)
            throw new ScoreStorageInsufficientSamplesException()
        scoreQueue.sum / 10
    }
}
