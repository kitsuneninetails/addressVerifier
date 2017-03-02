AddressFraudProbabilityScorer seemed to have a slight bug.  This code:

    private def scoreAddress(address: Address): Future[Double] = {
        if (simulateServiceFailure) {
            Future.failed(throw new Exception(s"Could not score address" +
                                              s"(address=$address)"))
        }
        Future.successful {
            val addressScore = Random.nextDouble()
            simulateServiceLatency()
            addressScore
        }
    }

will throw an exception while trying to fail the future, ending up with a future
that does not get filled.  Instead, the ask will time out.

However, changing the code to:

    private def scoreAddress(address: Address): Future[Double] = {
        if (simulateServiceFailure)
            Future.failed(
                new Exception(s"Could not score address (address=$address)"))
        else Future.successful {
            val addressScore = Random.nextDouble()
            simulateServiceLatency()
            addressScore
        }
    }

will make sure the future gets completed with a failure carrying the proper
exception, and will also make sure the Future.successful doesn't run on the
failure condition (thus causing an override issue).

With the above code, the web server will return the "Could not score address"
message immediaetly, rather than waiting for a timeout.