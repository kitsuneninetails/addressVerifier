package org.kitsuneninetails.paidytest.address_scorer

import com.paidy.domain.Address

class AddressScorer(val store: ScoreStore) {
    def score(addr: Address, score: Double): Boolean = {
        store.addScore(addr.hash(), score) > 70.0 || score > 75.0
    }
}
