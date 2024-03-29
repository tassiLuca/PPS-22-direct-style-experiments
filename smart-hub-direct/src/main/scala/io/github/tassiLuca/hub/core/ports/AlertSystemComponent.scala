package io.github.tassiLuca.hub.core.ports

import gears.async.Async

/** The component encapsulating the alert system port. */
trait AlertSystemComponent:

  /** The [[AlertSystem]] instance. */
  val alertSystem: AlertSystem

  /** The alert system port though which is possible to notify alerts. */
  trait AlertSystem:
    /** Notify an alert with the given [[message]]. */
    def notify(message: String)(using Async): Unit
