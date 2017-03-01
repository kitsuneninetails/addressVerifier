package org.kitsuneninetails.paidytest.address_scorer

trait ScoreStore {
    // Add a new score to storage and return the average
    def addScore(key: String, score: Double): Double
}

