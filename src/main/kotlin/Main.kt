import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive



suspend fun fetchFlight(passengerName: String): FlightStatus = coroutineScope {
    val client = HttpClient(CIO)

    val flightResponse = async {
        withContext(NonCancellable) {
            println("Начал получать информацию о рейсе")
            try {
                client.get<String>(FLIGHT_ENDPOINT).also {
                    println("Завершена выборка информации о рейсе")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    val loyaltyResponse = async {
        withContext(NonCancellable) {
            println("Начал получать информацию о лояльности")
            try {
                client.get<String>(LOYALTY_ENDPOINT).also {
                    println("Завершена выборка информации о лояльности")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }
    delay(500)
    println("Объединение полетных данных")
    FlightStatus.parse(
        passengerName = passengerName,
        flightResponse = flightResponse.await()!!,
        loyaltyResponse = loyaltyResponse.await()!!
    )
}

suspend fun findScheduledFlight(passengerName: String): FlightStatus {
    var flightStatus: FlightStatus? = null

    while (flightStatus == null || isActive) {
        try {
            flightStatus = fetchFlight(passengerName)
            if (!isSuccessful(flightStatus)) {
                println("Получен неверный статус рейса")
            }
        } catch (e: CancellationException) {
            println("Запрос отменен, выполняется новый запрос")
        } catch (e: Exception) {
            println("Возникла ошибка при выполнении запроса")
        }
    }

    return flightStatus!!
}

fun isSuccessful(flightStatus: FlightStatus): Boolean {
    // Проверка статуса рейса - осуществляется ли он по графику или задерживается
    // Верните true, если рейс соответствует условиям, иначе - false
    return flightStatus.status == "Scheduled" || flightStatus.status == "Delayed"
}