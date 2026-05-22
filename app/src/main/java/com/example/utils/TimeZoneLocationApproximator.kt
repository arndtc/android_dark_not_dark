package com.example.utils

import java.time.ZoneId
import java.util.TimeZone

object TimeZoneLocationApproximator {
    data class Coords(val latitude: Double, val longitude: Double, val cityName: String)

    private val tzMap = mapOf(
        "America/New_York" to Coords(40.7128, -74.0060, "New York"),
        "America/Los_Angeles" to Coords(34.0522, -118.2437, "Los Angeles"),
        "America/Chicago" to Coords(41.8781, -87.6298, "Chicago"),
        "America/Denver" to Coords(39.7392, -104.9903, "Denver"),
        "America/Phoenix" to Coords(33.4484, -112.0740, "Phoenix"),
        "America/Anchorage" to Coords(61.2181, -149.9003, "Anchorage"),
        "America/Honolulu" to Coords(21.3069, -157.8583, "Honolulu"),
        "America/Sao_Paulo" to Coords(-23.5505, -46.6333, "São Paulo"),
        "America/Mexico_City" to Coords(19.4326, -99.1332, "Mexico City"),
        "America/Bogota" to Coords(4.7110, -74.0721, "Bogotá"),
        "America/Argentina/Buenos_Aires" to Coords(-34.6037, -58.3816, "Buenos Aires"),
        
        "Europe/London" to Coords(51.5074, -0.1278, "London"),
        "Europe/Paris" to Coords(48.8566, 2.3522, "Paris"),
        "Europe/Berlin" to Coords(52.5200, 13.4050, "Berlin"),
        "Europe/Rome" to Coords(41.9028, 12.4964, "Rome"),
        "Europe/Madrid" to Coords(40.4168, -3.7038, "Madrid"),
        "Europe/Moscow" to Coords(55.7558, 37.6173, "Moscow"),
        "Europe/Athens" to Coords(37.9838, 23.7275, "Athens"),
        
        "Asia/Tokyo" to Coords(35.6762, 139.6503, "Tokyo"),
        "Asia/Seoul" to Coords(37.5665, 126.9780, "Seoul"),
        "Asia/Shanghai" to Coords(31.2304, 121.4737, "Shanghai"),
        "Asia/Kolkata" to Coords(22.5726, 88.3639, "Kolkata"),
        "Asia/Singapore" to Coords(1.3521, 103.8198, "Singapore"),
        "Asia/Dubai" to Coords(25.2048, 55.2708, "Dubai"),
        "Asia/Jakarta" to Coords(-6.2088, 106.8456, "Jakarta"),
        "Asia/Manila" to Coords(14.5995, 120.9842, "Manila"),
        
        "Australia/Sydney" to Coords(-33.8688, 151.2093, "Sydney"),
        "Australia/Melbourne" to Coords(-37.8136, 144.9631, "Melbourne"),
        "Australia/Perth" to Coords(-31.9505, 115.8605, "Perth"),
        
        "Africa/Cairo" to Coords(30.0444, 31.2357, "Cairo"),
        "Africa/Johannesburg" to Coords(-26.2041, 28.0473, "Johannesburg"),
        "Africa/Nairobi" to Coords(-1.2921, 36.8219, "Nairobi"),
        "Africa/Lagos" to Coords(6.5244, 3.3792, "Lagos"),
        
        "Pacific/Auckland" to Coords(-36.8485, 174.7633, "Auckland")
    )

    fun approximateCoordinates(zoneId: ZoneId): Coords {
        val zoneStr = zoneId.id
        
        // 1. Direct match
        tzMap[zoneStr]?.let { return it }

        // 2. Partial match based on city name in zone string (e.g. "Europe/London" or "America/New_York")
        val parts = zoneStr.split('/')
        if (parts.size >= 2) {
            val cityNameWithUnderscores = parts.last()
            val cleanCityName = cityNameWithUnderscores.replace('_', ' ')
            
            // Look for matching city in our map
            val match = tzMap.values.find { it.cityName.equals(cleanCityName, ignoreCase = true) }
            if (match != null) {
                return match
            }
        }

        // 3. Mathematical approximation fallback
        val tz = TimeZone.getTimeZone(zoneId)
        val offsetHours = tz.rawOffset / 3600000.0
        val longitude = offsetHours * 15.0
        
        // Default to a representative mid-latitude
        val latitude = if (zoneStr.contains("America") || zoneStr.contains("Europe") || zoneStr.contains("Asia")) {
            35.0 // Northern hemisphere default
        } else if (zoneStr.contains("Australia") || zoneStr.contains("Pacific") || zoneStr.contains("Indian")) {
            -30.0 // Southern hemisphere default
        } else {
            15.0 // Equatorial-leaning default
        }
        
        val displayCityName = parts.lastOrNull()?.replace('_', ' ') ?: zoneStr
        return Coords(latitude, longitude, displayCityName)
    }
}
