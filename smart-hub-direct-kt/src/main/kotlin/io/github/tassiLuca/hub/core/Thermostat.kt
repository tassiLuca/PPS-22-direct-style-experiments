package io.github.tassiLuca.hub.core

import io.github.tassiLuca.hub.core.ports.DashboardService
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/** A thermostat. */
class Thermostat(
    private val targetTemperature: Temperature,
    override val samplingWindow: Duration,
    private val dashboardService: DashboardService,
    override val coroutineContext: CoroutineContext,
) : ScheduledConsumer<TemperatureEntry, List<TemperatureEntry>> {

    @get:Synchronized @set:Synchronized
    override var state: List<TemperatureEntry> = emptyList()

    override suspend fun react(e: TemperatureEntry) {
        state = state + e
    }

    override suspend fun update() {
        if (state.isNotEmpty()) {
            val average = state.map { it.temperature }.average()
            dashboardService.temperatureUpdated(average)
            if (average > targetTemperature + HYSTERESIS) {
                dashboardService.offHeaterNotified()
            } else if (average < targetTemperature) {
                dashboardService.onHeaterNotified()
            }
            state = emptyList()
        }
    }

    companion object {
        private const val HYSTERESIS = 1.5
    }
}
