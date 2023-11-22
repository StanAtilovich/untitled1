import BoardingState.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.flow.*

val bannedPassengers = setOf("Nogartse")

fun main() {
    runBlocking {
        println("Getting the latest flight info...")
        val flights = fetchFlights()
        val fightDescriptions = flights.joinToString {
            "${it.passangerName} (${it.flightNumber})"
        }
        println("Found flights for $fightDescriptions")
        val flightsAtGate = MutableStateFlow(flights.size)
        launch {
            flightsAtGate
                .takeWhile { it > 0 }
                .onCompletion {
                    println("Finished tracking all flights")
                }
                .collect { flightCount ->
                    println("There are $flightCount flights being tracked")
                }
        }
        launch {
            flights.forEach {
                watchFlight(it)
                flightsAtGate.value = flightsAtGate.value - 1
            }
        }
    }
}

suspend fun watchFlight(initialFlight: FlightStatus) {
    val passengerName = initialFlight.passangerName
    val currentFlight: Flow<FlightStatus> = flow {
        require(passengerName !in bannedPassengers) {
            "Cannot track $passengerName s flight. There are banned from the airport"
        }
        var flight = initialFlight
        while (flight.departureTimeInMinutes >= 0 && !flight.isFlightCanceled) {
            emit(flight)
            delay(1000)
            flight = flight.copy(
                departureTimeInMinutes = flight.departureTimeInMinutes - 1
            )
        }
    }
    currentFlight
        .map { flight ->
            when (flight.boardingStatus) {
                FlightCanceled -> "You was canceled"
                BoardingNotStarted -> "Boarding will start soon"
                WaitingToBoard -> "Other passengers are boarding"
                Boarding -> "You can now boar the plane"
                BoardingEnded -> " The boarding doors have closed"
            } + "(Flight departs in ${flight.departureTimeInMinutes} minutes)"
        }
        .onCompletion {
            println("Finished tracking $passengerName s flight")
        }
        .collect { status ->
            println("$passengerName: $status")
        }
    println("Finished tracking $passengerName s flight")
}

suspend fun fetchFlights(
    passangerNames: List<String> = listOf("Madrigal", "Polarcubis", "Estragon", "Taernyl"),
    numberOfWorkers: Int = 2
): List<FlightStatus> = coroutineScope {
    val pasangerNamesChannel = Channel<String>()
    val fetchFlightsChannel = Channel<FlightStatus>()
    launch {
        passangerNames.forEach {
            pasangerNamesChannel.send(it)
        }
        pasangerNamesChannel.close()
    }
    launch {
        (1..numberOfWorkers).map {
            launch {
                fetchFlightsStatuses(pasangerNamesChannel,fetchFlightsChannel)
            }
        }.joinAll()
        fetchFlightsChannel.close()
    }
    fetchFlightsChannel.toList()
}

suspend fun fetchFlightsStatuses(
    fetchChannel: ReceiveChannel<String>,
    resultChannel: SendChannel<FlightStatus>
) {
    for (passangerNames in fetchChannel) {
        val flight = fetchFlight(passangerNames)
        println("Fetch flight:$flight")
        resultChannel.send(flight)
    }
}


