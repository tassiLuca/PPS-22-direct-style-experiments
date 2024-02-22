package io.github.tassiLuca.smarthome.core

import java.time.{DayOfWeek, LocalDateTime}

trait ThermostatScheduler:
  type TimeSpan = (Int, Int)
  type Temperature = Double
  type Day = DayOfWeek

  def update(day: Day, timeSpan: TimeSpan, target: Temperature): Unit
  def targetFor(date: LocalDateTime): Temperature
  def currentTarget: Temperature = targetFor(LocalDateTime.now())

  extension (date: LocalDateTime) def in(t: TimeSpan): Boolean = t._1 <= date.getHour && t._2 >= date.getHour

object ThermostatScheduler:

  def apply(targetTemperature: Double): ThermostatScheduler = ThermostatSchedulerImpl(targetTemperature)

  private class ThermostatSchedulerImpl(val targetTemperature: Double) extends ThermostatScheduler:
    private var schedule: Map[(DayOfWeek, (Int, Int)), Double] = Map()

    override def targetFor(date: LocalDateTime): Temperature = schedule
      .find((d, _) => date.getDayOfWeek == d._1 && (date in d._2))
      .map(_._2)
      .getOrElse(targetTemperature)

    override def update(day: Day, timeSpan: TimeSpan, target: Temperature): Unit =
      require(timeSpan._1 < timeSpan._2)
      if schedule.isEmpty then schedule = schedule + ((day, timeSpan) -> target)
      else
        val (nonOverlapping, overlapping) = schedule.partition { case ((d, (start, end)), _) =>
          d == day && (end <= timeSpan._1 || start >= timeSpan._2)
        }
        val updatedEntries = overlapping
          .flatMap { case ((day, (start, end)), currentTarget) =>
            List(
              (day, (start, timeSpan._1)) -> currentTarget,
              (day, (timeSpan._2, end)) -> currentTarget,
            )
          }
          .filter { case ((_, (start, end)), _) => start < end }
        schedule = (nonOverlapping ++ updatedEntries) + ((day, timeSpan) -> target)
