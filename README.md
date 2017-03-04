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
        "success": TRUE or FALSE,
        "score": The score for this individual assessment,
        "average": The average of the last ten assessments (or this score if
                   there are less then ten assessments recorded).
     }
     
The "success" field will be `FALSE` if `score` < 0.78 or if `average` < 0.70
*AND* there were at least ten assessments to draw an average from (this
parameter will be ignored otherwise).  Otherwise, the field will be `TRUE`.

In case of an error on the server side (through timeout or error in processing 
the request), the HTTP response will have a status code of `500` and a
message will be provided indicating the reason for the server error.

Implementation
------------

The REST API web server is implemented with Akka HTTP, as it was fairly
straightforward and simple to get a working REST API server with minimal work.
 
The web server is itself implemented as an actor using the Akka Actors library,
to give it flexibility in its own running space, so it can handle both receiving
requests from clients and passing messages off to the score assessor.  The start
and stop messages to the web server will handle the web server's state, as well 
as handle the HTTP server's actor system (separate from the main actor system as 
there should be no relationship between the two outside of the REST API handler 
function(s)). The web server creates a child actor which will handle the final 
verdict of the assessment and pass the result back to the web server to send to 
the client. When a REST API request is handled, the score assessor will be sent 
a message to calculate the score result, and that result sent back to the REST 
API caller.

The score assessor is also an actor, which will communicate with both its 
storage medium and the provided `AddressFraudProbabilityScorer` actor. 
For simplicity in this case, the storage medium is implemented as an in-memory, 
mutable queue limited to 10 items to make it easier to contain growth over time, 
as only the last 10 items are required for the score averaging.  In other cases,
this could be implemented as a database connector or other client to a remote 
data store.

The communication between actors is handled with Actor messaging, using "ask" 
requests to receive a Scala Future object, which can then be handled 
asynchronously.  Only the web server itself will wait on the result of the 
future, as it must have that information before it can satisfy the REST API 
call.  The other actors will pipe futures back to the caller, whether they be 
successful or indicative of failure conditions.

A hard timeout of 5 seconds is specified for the future Await as well as all 
Ask messages between actors, as that was a very specific requirement.  If this 
timeout passes, an appropriate response will be sent back to the client via a 
HTTP Response with a status code of `500`.
 
_Note: The included SBT file is purely to pull Akka dependencies into my 
IntelliJ IDE._

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