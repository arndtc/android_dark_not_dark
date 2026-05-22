package com.example.utils

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*

sealed class SolarResult {
    data class Normal(val sunrise: ZonedDateTime, val sunset: ZonedDateTime) : SolarResult()
    object PolarDay : SolarResult()
    object PolarNight : SolarResult()
}

object SolarCalculator {
    /**
     * Calculates sunrise and sunset for a given date, latitude, and longitude.
     */
    fun calculateSunriseSunset(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId
    ): SolarResult {
        val udt = date.atStartOfDay(zoneId)
        val offsetHours = (zoneId.rules.getOffset(udt.toInstant()).totalSeconds) / 3600.0

        // 1. Convert the day of the year to double
        val n = date.dayOfYear.toDouble()

        // 2. Convert longitude to hour value and estimate approximate sunrise/sunset time
        val lngHour = longitude / 15.0
        val tSunrise = n + ((6.0 - lngHour) / 24.0)
        val tSunset = n + ((18.0 - lngHour) / 24.0)

        // Track polar status
        var isPolarNight = false
        var isPolarDay = false

        fun calcTime(t: Double, isSunrise: Boolean): Double? {
            // 3. Sun's mean anomaly
            val m = (0.9856 * t) - 3.289

            // 4. Sun's true longitude
            var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2.0 * m))) + 282.634
            l = (l % 360.0 + 360.0) % 360.0

            // 5a. Sun's right ascension
            var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
            ra = (ra % 360.0 + 360.0) % 360.0

            // 5b. Same quadrant adjustment
            val lQuadrant = floor(l / 90.0) * 90.0
            val raQuadrant = floor(ra / 90.0) * 90.0
            ra = ra + (lQuadrant - raQuadrant)

            // 5c. Right ascension in hours
            val raHours = ra / 15.0

            // 6. Declination of the Sun
            val sinDec = 0.39782 * sin(Math.toRadians(l))
            val cosDec = cos(asin(sinDec))

            // 7a. Sun's local hour angle (Zenith: 90.8333 degrees for civil twilight sunrise/sunset)
            val zenithRad = Math.toRadians(90.8333)
            val latRad = Math.toRadians(latitude)
            val cosH = (cos(zenithRad) - (sinDec * sin(latRad))) / (cosDec * cos(latRad))

            if (cosH > 1.0) {
                isPolarNight = true
                return null
            }
            if (cosH < -1.0) {
                isPolarDay = true
                return null
            }

            // 7b. Sunrise/sunset calculations
            val h = if (isSunrise) {
                360.0 - Math.toDegrees(acos(cosH))
            } else {
                Math.toDegrees(acos(cosH))
            }
            val hHours = h / 15.0

            // 8. Local mean time
            val localMeanTime = hHours + raHours - (0.06571 * t) - 6.622

            // 9. UTC time
            var utcTime = localMeanTime - lngHour
            utcTime = (utcTime % 24.0 + 24.0) % 24.0

            // 10. Local time
            val localTimeHours = utcTime + offsetHours
            return (localTimeHours % 24.0 + 24.0) % 24.0
        }

        val sunriseHours = calcTime(tSunrise, isSunrise = true)
        val sunsetHours = calcTime(tSunset, isSunrise = false)

        if (isPolarNight) {
            return SolarResult.PolarNight
        }
        if (isPolarDay) {
            return SolarResult.PolarDay
        }
        if (sunriseHours == null || sunsetHours == null) {
            // Backup check
            return if (latitude > 0 && (n in 80.0..264.0)) SolarResult.PolarDay else SolarResult.PolarNight
        }

        fun hoursToTime(hours: Double): LocalTime {
            val totalSeconds = (hours * 3600.0).roundToInt()
            val finalSeconds = (totalSeconds % 86400 + 86400) % 86400
            val h = finalSeconds / 3600
            val m = (finalSeconds % 3600) / 60
            val s = finalSeconds % 60
            return LocalTime.of(h, m, s)
        }

        val sunriseLocal = date.atTime(hoursToTime(sunriseHours)).atZone(zoneId)
        val sunsetLocal = date.atTime(hoursToTime(sunsetHours)).atZone(zoneId)

        return SolarResult.Normal(sunriseLocal, sunsetLocal)
    }
}
