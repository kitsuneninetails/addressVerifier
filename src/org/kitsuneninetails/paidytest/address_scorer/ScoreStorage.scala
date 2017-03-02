package org.kitsuneninetails.paidytest.address_scorer

trait ScoreStorage {
    // Add a new score to storage and return the average
    def addAndAverage(key: String, score: Double): Double
}

