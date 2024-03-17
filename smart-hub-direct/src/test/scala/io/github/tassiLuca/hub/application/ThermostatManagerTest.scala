package io.github.tassiLuca.hub.application

import gears.async.TaskSchedule.Every
import gears.async.default.given
import gears.async.{Async, AsyncOperations, Channel, ReadableChannel, SendableChannel, Task, UnboundedChannel}
import io.github.tassiLuca.hub.core.TemperatureEntry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ThermostatManagerTest extends AnyFlatSpec with Matchers {

  private val thermostatManager = TestableThermostatManager

  "The thermostat" should "receive event from the source" in {
    Async.blocking:
      val (channel, task) = sensorSource[TemperatureEntry]: c =>
        c.send(TemperatureEntry("t1", 0L))
        c.send(TemperatureEntry("t2", 2L))
      thermostatManager.run(channel.asReadable)
      task.start().awaitResult
      AsyncOperations.sleep(samplingWindow.toMillis + 1_000)
      thermostatManager.heaterActions should contain(Message.HeaterOn)
      thermostatManager.thermostat.state should be(Some(1.0))
      thermostatManager.alerts should not contain Message.Alert
      channel.close()
  }

  "The checker" should "note possible malfunctioning" in {
    val sensorNames = scala.collection.mutable.Set("t1", "t2", "t3", "t4")
    Async.blocking:
      val (channel, task) = sensorSource[TemperatureEntry]: c =>
        c.send(TemperatureEntry(sensorNames.head, 0L))
        c.send(TemperatureEntry(sensorNames.head, 1L))
      thermostatManager.run(channel.asReadable)
      task.schedule(Every(samplingWindow.toMillis, maxRepetitions = 2)).start().awaitResult
      AsyncOperations.sleep(samplingWindow.toMillis + 1_000)
      thermostatManager.alerts should contain(Message.Alert)
      channel.close()
  }
}
