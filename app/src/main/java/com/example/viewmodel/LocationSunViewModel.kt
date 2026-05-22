package com.example.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.utils.SolarCalculator
import com.example.utils.SolarResult
import com.example.utils.TimeZoneLocationApproximator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class LocationSourceType {
    GPS, TIMEZONE, PRESET, CUSTOM
}

data class PresetCity(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: ZoneId,
    val description: String
)

data class AppUiState(
    // Mode
    val isSimulationMode: Boolean = false,
    
    // Coordinates & Info
    val latitude: Double = 40.7128,
    val longitude: Double = -74.0060,
    val locationSourceName: String = "New York, NY",
    val locationSourceType: LocationSourceType = LocationSourceType.TIMEZONE,
    
    // Time details
    val systemTime: LocalTime = LocalTime.now(),
    val systemDate: LocalDate = LocalDate.now(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
    
    // Simulated Time parameters
    val simulatedHour: Float = 12.0f, // 0.0f to 23.99f
    
    // Solar outputs
    val solarResult: SolarResult = SolarResult.PolarDay, // Default indicator
    val sunriseText: String = "--:--",
    val sunsetText: String = "--:--",
    
    // States
    val isDark: Boolean = false,
    val statusText: String = "Not Dark",
    val statusReason: String = "Calculating...",
    
    // GPS loading/errors
    val isGpsLoading: Boolean = false,
    val gpsError: String? = null
)

class LocationSunViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    // Preset Cities for easy simulation
    val presets = listOf(
        PresetCity("Svalbard, Norway", 78.2232, 15.6267, ZoneId.of("Europe/Oslo"), "Arctic - Polar Day/Night"),
        PresetCity("New York, USA", 40.7128, -74.0060, ZoneId.of("America/New_York"), "East Coast Metro"),
        PresetCity("London, UK", 51.5074, -0.1278, ZoneId.of("Europe/London"), "Prime Meridian"),
        PresetCity("Tokyo, Japan", 35.6762, 139.6503, ZoneId.of("Asia/Tokyo"), "Japan Standard Time"),
        PresetCity("Sydney, Australia", -33.8688, 151.2093, ZoneId.of("Australia/Sydney"), "Southern Hemisphere Seasons"),
        PresetCity("Cairo, Egypt", 30.0444, 31.2357, ZoneId.of("Africa/Cairo"), "Equatorial-adjacent desert")
    )

    init {
        // Initialize based on current system parameters
        initializeWithTimezone()
    }

    fun initializeWithTimezone() {
        val defaultZone = ZoneId.systemDefault()
        val approx = TimeZoneLocationApproximator.approximateCoordinates(defaultZone)
        
        _uiState.update { state ->
            state.copy(
                latitude = approx.latitude,
                longitude = approx.longitude,
                locationSourceName = approx.cityName,
                locationSourceType = LocationSourceType.TIMEZONE,
                zoneId = defaultZone,
                systemTime = LocalTime.now(),
                systemDate = LocalDate.now(),
                simulatedHour = LocalTime.now().toSecondOfDay() / 3600f
            )
        }
        recalculateSolar()
    }

    fun toggleSimulationMode(enabled: Boolean) {
        _uiState.update { it.copy(isSimulationMode = enabled) }
        recalculateSolar()
    }

    fun updateSimulatedHour(hour: Float) {
        _uiState.update { it.copy(simulatedHour = hour.coerceIn(0f, 23.99f)) }
        recalculateSolar()
    }

    fun selectPreset(preset: PresetCity) {
        _uiState.update { state ->
            state.copy(
                latitude = preset.latitude,
                longitude = preset.longitude,
                locationSourceName = preset.name,
                locationSourceType = LocationSourceType.PRESET,
                zoneId = preset.zoneId,
                isSimulationMode = true // automatically engage simulation mode for presets to let user play with it
            )
        }
        recalculateSolar()
    }

    fun setCustomCoordinates(lat: Double, lng: Double, cityName: String = "Custom Location") {
        _uiState.update { state ->
            state.copy(
                latitude = lat.coerceIn(-90.0, 90.0),
                longitude = lng.coerceIn(-180.0, 180.0),
                locationSourceName = cityName,
                locationSourceType = LocationSourceType.CUSTOM
            )
        }
        recalculateSolar()
    }

    fun updateCurrentTime() {
        if (!_uiState.value.isSimulationMode) {
            _uiState.update { state ->
                state.copy(
                    systemTime = LocalTime.now(),
                    systemDate = LocalDate.now(),
                    simulatedHour = LocalTime.now().toSecondOfDay() / 3600f
                )
            }
            recalculateSolar()
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchGpsLocation(context: Context) {
        _uiState.update { it.copy(isGpsLoading = true, gpsError = null) }
        
        viewModelScope.launch {
            try {
                val appContext = context.applicationContext
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
                val cts = CancellationTokenSource()
                
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).addOnSuccessListener { location: Location? ->
                    try {
                        if (location != null) {
                            val lat = location.latitude
                            val lng = location.longitude
                            
                            _uiState.update { state ->
                                state.copy(
                                    latitude = lat,
                                    longitude = lng,
                                    locationSourceName = "GPS Location",
                                    locationSourceType = LocationSourceType.GPS,
                                    isGpsLoading = false,
                                    gpsError = null
                                )
                            }
                            recalculateSolar()
                        } else {
                            // Fall back to last location within internal try-catch
                            try {
                                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                                    try {
                                        if (lastLoc != null) {
                                            _uiState.update { state ->
                                                state.copy(
                                                    latitude = lastLoc.latitude,
                                                    longitude = lastLoc.longitude,
                                                    locationSourceName = "GPS (Last)",
                                                    locationSourceType = LocationSourceType.GPS,
                                                    isGpsLoading = false,
                                                    gpsError = null
                                                )
                                            }
                                            recalculateSolar()
                                        } else {
                                            _uiState.update { state ->
                                                state.copy(
                                                    isGpsLoading = false,
                                                    gpsError = "GPS coordinates unavailable. Check if GPS is enabled."
                                                )
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        _uiState.update { state ->
                                            state.copy(
                                                isGpsLoading = false,
                                                gpsError = "Error reading last location: ${t.localizedMessage}"
                                            )
                                        }
                                    }
                                }.addOnFailureListener { err ->
                                    _uiState.update { state ->
                                        state.copy(
                                            isGpsLoading = false,
                                            gpsError = "Location lookup error: ${err.localizedMessage}"
                                        )
                                    }
                                }
                            } catch (t: Throwable) {
                                _uiState.update { state ->
                                    state.copy(
                                        isGpsLoading = false,
                                        gpsError = "Uncaught GPS fallback crash: ${t.localizedMessage}"
                                    )
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        _uiState.update { state ->
                            state.copy(
                                isGpsLoading = false,
                                gpsError = "GPS process failed: ${t.localizedMessage}"
                            )
                        }
                    }
                }.addOnFailureListener { err ->
                    _uiState.update { state ->
                        state.copy(
                            isGpsLoading = false,
                            gpsError = "GPS timeout or error: ${err.localizedMessage}"
                        )
                    }
                }
            } catch (t: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        isGpsLoading = false,
                        gpsError = "GPS services unavailable: ${t.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun recalculateSolar() {
        val state = _uiState.value
        val dateToCheck = state.systemDate
        val zone = state.zoneId
        
        // Calculate solar stats
        val solarResult = SolarCalculator.calculateSunriseSunset(dateToCheck, state.latitude, state.longitude, zone)
        
        // Determine the "current time of evaluation" (system or simulated)
        val evalTime = if (state.isSimulationMode) {
            val totalSeconds = (state.simulatedHour * 3600).roundToInt()
            val hour = (totalSeconds / 3600) % 24
            val minute = (totalSeconds % 3600) / 60
            val second = totalSeconds % 60
            LocalTime.of(hour, minute, second)
        } else {
            LocalTime.now()
        }

        val formatter = DateTimeFormatter.ofPattern("hh:mm a")

        var isDark = false
        var statusText = "Not Dark"
        var statusReason = "Daylight Active"
        var sunriseStr = "--:--"
        var sunsetStr = "--:--"

        when (solarResult) {
            is SolarResult.Normal -> {
                val riseTime = solarResult.sunrise.toLocalTime()
                val setTime = solarResult.sunset.toLocalTime()
                
                sunriseStr = solarResult.sunrise.toLocalTime().format(formatter)
                sunsetStr = solarResult.sunset.toLocalTime().format(formatter)

                if (riseTime.isBefore(setTime)) {
                    // Standard day-night sequence
                    isDark = evalTime.isBefore(riseTime) || evalTime.isAfter(setTime)
                    statusReason = if (isDark) "Sun is below horizon" else "Daylight Active"
                } else {
                    // Negative day transition sequence
                    isDark = evalTime.isAfter(setTime) && evalTime.isBefore(riseTime)
                    statusReason = if (isDark) "Sun is below horizon" else "Daylight Active"
                }
            }
            is SolarResult.PolarDay -> {
                isDark = false
                statusText = "Not Dark"
                statusReason = "Midnight Sun (Polar Day)"
                sunriseStr = "No rise"
                sunsetStr = "No set"
            }
            is SolarResult.PolarNight -> {
                isDark = true
                statusText = "Dark"
                statusReason = "Midnight Dark (Polar Night)"
                sunriseStr = "No rise"
                sunsetStr = "No set"
            }
        }

        if (solarResult is SolarResult.Normal) {
            statusText = if (isDark) "Dark" else "Not Dark"
        }

        _uiState.update { s ->
            s.copy(
                solarResult = solarResult,
                sunriseText = sunriseStr,
                sunsetText = sunsetStr,
                isDark = isDark,
                statusText = statusText,
                statusReason = statusReason
            )
        }
    }
}
