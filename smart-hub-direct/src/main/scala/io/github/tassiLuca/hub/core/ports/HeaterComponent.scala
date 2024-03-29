package io.github.tassiLuca.hub.core.ports

import gears.async.Async

/** The component encapsulating the heater port. */
trait HeaterComponent:

  /** The instance in charge of controlling heater actuator. */
  val heater: Heater

  /** Heater actuator port through which is possible to turning on and off the heater. */
  trait Heater:
    enum HeaterState:
      case ON, OFF

    /** Turn on the heater. */
    def on()(using Async): Unit

    /** Turn off the heater. */
    def off()(using Async): Unit

    /** The current state of the heater, i.e. [[HeaterState]]- */
    def state: HeaterState
