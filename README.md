AddressVerifier
===============

This project implements the `addressScorer` API, which can be accessed through
a *POST* HTTP request to `http://server:port/addressScorer`.  The body of the 
POST message should be a JSON with the following structure:

    {
      "line1": ADDR_LINE_1,
      "line2": ADDR_LINE_2,
      "city": CITY,
      "state": STATE,
      "zipCode": ZIP
    }

The return reponse will contain a JSON message of the following structure:
    
    {
        "success": TRUE or FALSE
     }
     
The "success" field will be `FALSE` if `score` > 0.78 or if there is at least
ten scores recorded for this address (including the current score) and the
average of those ten scores > 0.70.  Otherwise, the field will be `TRUE`.

In case of an error on the server side (through timeout or error in processing 
the request), the HTTP response will have a status code of `500` and a
message will be provided indicating the reason for the server error.

Implementation
------------

The REST API web server is implemented with Akka HTTP, as it was fairly
straightforward and simple to get a working REST API server with minimal work.
It is implemented in the main application method.

The score assessor `passOrFail` is implemented as a high-level function which
takes two functions as parameters, "`sf`" to test the score, and "`af`" to test
a list of scores.  A second function `passOrFail78And70` partially applies the
`passFail` function to use a (score < 0.78) and an (average of list elements < 0.70)
as the assessment functions, as those were the required levels set by the problem
statement.  The `passOrFail` will make a list with the newest score as the head,
and the last nine scores as the tail.  It will then return:

    sf(current score) && af(current score :: past 9 scores)

and

    currentScore :: past 9 scores

as a return value.  The return value is encapsulated in a case class called
`PFReturn` (which has a Boolean and a List) for organization's sake.

The past scores are stored in memory, via an actor called `CurrentScores`.  This
actos has a variable (immutable) map of address keys to score lists.  This map
will be reassigned to a new, updated map each time the `SetScores` message is
received with an address key and a new score list to use for that key.  The
`GetScore` method will return the score list for a given address key.  This way
the actual creation of the new score list is handled by the caller of
`SetScores` and the private variable for the map is hidden and accessible only
via the `SetScores` method, which should make it easier to identify the
side-effect of reassigning `CurrentScores`'s map.

The flow is:

* For every score request that enters the web server:
  * A Future chain begins with the call to the provided
`AddressFraudProbabilityScorer` with the address sent by the HTTP client.
  * This chains to a `GetScores` call to the in-memory storage actor
`CurrentScores` in order to get the list of past scores for this address key.
  * This finally chains to the `passOrFail78And70` call to assess whether
the score passed or failed.
  * The return from this chain is a `Future[PFReturn]`.  If any part of the
above chain (except the last `passOrFail` call) fails, this future will be a 
`Failure` (and no further calls in the chain will be made).  If every part of
the chain succeeds, this return will be a `Success(PFReturn)` with the returned
value from `passOrFail78And70` to be set when the future completes.
* The flow then blocks on return of this future chain, with a 5 second timeout
(also set by the problem statement).  This block is accomplished with a `Try`
function, meaning a failure to return within the timeout will be handled as a
failed Future, which aligns with the possible failed future from above.  This
means that any failure along the way will be set to a `Failure` object, and can
be handled with one pattern matching block.
* The return of the blocking `Try` is pattern matched and handled.
  * A `Success` condition will first make an asychronous message (i.e. tell) to
the `CurrentScores` actor to reset the score list for this address key to the
list returned by the `passOrFail78And70` call above.  It will then return the
pass/fail result to the HTTP client.
  * A `Failure` condition is matched to see which exception was thrown, in
order to provide a cleaner message to the user in case of a timeout (users don't
need to see "Futures timed out message", so instead a "API timeout" message
will be substituted in the HTTP Response), and an HTTP error response is
returned.

For the purposes of simplicty and clarity for this problem, the HTTP server is
simply started in the Main app and is terminated with the press of "ENTER" at
the console.

Building and Running
-----

This project has a `build.sbt` file, so to build, please run:

    sbt compile
    
This should download akka and other dependencies.  The project also defines a 
`Main` object, so you can:

    sbt run
    
to run the server.  

Notes
-----

The AddressFraudProbabilityScorer seemed to have a slight issue with creating a 
Future for a timeout failure condition.  This code:

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

will actually throw the exception while trying to fail the future, ending up 
with a future that does not get filled.  Instead, the ask will time out with an 
AskTimeoutException after the timeout period.

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
exception (the Future.failed() function takes a Throwable parameter), and 
will also make sure the Future.successful doesn't get run on the failure
condition.

With the above code, the web server will return the "Could not score address"
message immediaetly, rather than waiting for a timeout.

Testing
-------

Testing was done with `curl` via the command line as well as with a Python
module (included as clientTester.py) written with the `curl_utils` module from:

http://github.com/kitsuneninetails/python-utils