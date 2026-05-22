package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LocationSunViewModel
import com.example.viewmodel.LocationSourceType
import com.example.viewmodel.AppUiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContentScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainContentScreen(
    viewModel: LocationSunViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isSimulationDashboardOpen by remember { mutableStateOf(false) }

    // Multi-location permission requesting cleanly integrated
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Side-Effect: Immediately resolve GPS location if permission was already granted previously
    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            viewModel.fetchGpsLocation(context)
        }
    }

    // Dynamic Clock tick to keep app accurate when simulation is inactive
    LaunchedEffect(state.isSimulationMode) {
        while (!state.isSimulationMode) {
            viewModel.updateCurrentTime()
            delay(15000) // update every 15 seconds
        }
    }

    // Display location retrieval errors if any
    LaunchedEffect(state.gpsError) {
        state.gpsError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        }
    }

    // Dynamic background visual shifting calculations
    val baseColor1 by animateColorAsState(
        targetValue = if (state.isDark) Color(0xFF090911) else Color(0xFFFFFAD6),
        animationSpec = tween(1000)
    )
    val baseColor2 by animateColorAsState(
        targetValue = if (state.isDark) Color(0xFF131126) else Color(0xFFFDF1B8),
        animationSpec = tween(1000)
    )

    // Ambient Radial light source glow shifting animation (Not too bright warm yellow / cosmic midnight indigo)
    val lightGlowCenterColor by animateColorAsState(
        targetValue = if (state.isDark) Color(0xFF1D1735) else Color(0x73FFFFFF),
        animationSpec = tween(1000)
    )
    val lightGlowOuterColor by animateColorAsState(
        targetValue = if (state.isDark) Color(0x00080812) else Color(0x00FDF1B8),
        animationSpec = tween(1000)
    )

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(baseColor1, baseColor2)))
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            // Dynamic Radial brush
            val radialBrush = Brush.radialGradient(
                colors = listOf(lightGlowCenterColor, lightGlowOuterColor),
                center = Offset(widthPx / 2f, heightPx * 0.45f),
                radius = min(widthPx, heightPx) * 0.85f
            )

            // Draw dynamic luminous orb backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(radialBrush)
            )

            // Layout Container keeping content single-page, responsive, notch & 3-button navigation safe!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // 1. Header (Translucent glass style buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left GPS location status block
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (permissionState.allPermissionsGranted) {
                                    viewModel.fetchGpsLocation(context)
                                } else {
                                    permissionState.launchMultiplePermissionRequest()
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val headerIconBgColor = if (state.isDark) Color(0x33FFFFFF) else Color(0x40FFFFFF)
                        val headerIconColor = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300)

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(headerIconBgColor)
                                .testTag("location_indicator_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isGpsLoading) {
                                CircularProgressIndicator(
                                    color = headerIconColor,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = "Trigger GPS localization status",
                                    tint = headerIconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "LOCATION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isDark) Color(0x88FFFFFF) else Color(0x88423300),
                                letterSpacing = 1.8.sp
                            )
                            Text(
                                text = state.locationSourceName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Settings/Sliders trigger block
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (state.isDark) Color(0x33FFFFFF) else Color(0x40FFFFFF))
                            .clickable {
                                isSimulationDashboardOpen = !isSimulationDashboardOpen
                            }
                            .testTag("simulation_toggle_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSimulationDashboardOpen) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "Toggle simulator dashboard control panel",
                            tint = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 2. Middle Visualizer Area (Weighted 1f so everything stays beautifully inside boundaries)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = isSimulationDashboardOpen,
                        transitionSpec = {
                            slideInVertically { height -> height } + fadeIn() togetherWith
                                    slideOutVertically { height -> -height } + fadeOut()
                        },
                        label = "MainContentTransition"
                    ) { open ->
                        if (open) {
                            // High Fidelity Simulation Lab
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .shadow(16.dp, RoundedCornerShape(28.dp))
                                    .testTag("simulation_settings_panel"),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (state.isDark) Color(0xEE1E1A35) else Color(0xEEFFFDEF)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (state.isDark) Color(0x25FFFFFF) else Color(0x1F423300)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Header controls inside Simulation Menu
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Simulation Canvas",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Sim mode",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (state.isDark) Color(0x88FFFFFF) else Color(0x88423300)
                                            )
                                            Switch(
                                                checked = state.isSimulationMode,
                                                onCheckedChange = { viewModel.toggleSimulationMode(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300),
                                                    checkedTrackColor = if (state.isDark) Color(0xFF4B3A8B) else Color(0xFFE6D695)
                                                ),
                                                modifier = Modifier
                                                    .scale(0.85f)
                                                    .testTag("activate_sim_switch")
                                            )
                                        }
                                    }

                                    // Dynamic simulated hour controller
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val simHourInt = state.simulatedHour.toInt()
                                        val simMinInt = ((state.simulatedHour - simHourInt) * 60).roundToInt()
                                        val amPm = if (simHourInt >= 12) "PM" else "AM"
                                        val twelveHour = if (simHourInt % 12 == 0) 12 else simHourInt % 12
                                        val formattedTime = String.format("%02d:%02d %s", twelveHour, simMinInt, amPm)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Simulate Day Hour",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (state.isDark) Color(0xCCFFFFFF) else Color(0xCC423300)
                                            )
                                            Text(
                                                text = formattedTime,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300)
                                            )
                                        }

                                        Slider(
                                            value = state.simulatedHour,
                                            onValueChange = {
                                                viewModel.toggleSimulationMode(true)
                                                viewModel.updateSimulatedHour(it)
                                            },
                                            valueRange = 0f..23.99f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300),
                                                activeTrackColor = if (state.isDark) Color(0xFFE9D581) else Color(0xFF423300),
                                                inactiveTrackColor = if (state.isDark) Color(0x22FFFFFF) else Color(0x1F423300)
                                            ),
                                            modifier = Modifier.testTag("sim_time_slider")
                                        )
                                    }

                                    // Preset Coordinates for immediate check
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "QUICK PRESETS (FOR TESTING LATITUDES)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (state.isDark) Color(0x7FBCB7D6) else Color(0x7F423300),
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(end = 4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(viewModel.presets) { preset ->
                                                val isSelectedPreset = state.locationSourceName == preset.name && state.locationSourceType == LocationSourceType.PRESET
                                                val presetBorder = if (isSelectedPreset) BorderStroke(1.5.dp, if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300)) else null

                                                Surface(
                                                    onClick = { viewModel.selectPreset(preset) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = presetBorder,
                                                    color = if (isSelectedPreset) {
                                                        if (state.isDark) Color(0x2BFFFFFF) else Color(0x2E423300)
                                                    } else {
                                                        if (state.isDark) Color(0x11FFFFFF) else Color(0x0A423300)
                                                    },
                                                    modifier = Modifier.testTag("preset_pill_${preset.name.replace(",", "").replace(" ", "_")}")
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            text = preset.name.split(',')[0],
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300)
                                                        )
                                                        Text(
                                                            text = preset.description,
                                                            fontSize = 7.5.sp,
                                                            color = if (state.isDark) Color(0x99FFFFFF) else Color(0x99423300)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Action Operations reset back and reload GPS
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.toggleSimulationMode(false)
                                                viewModel.initializeWithTimezone()
                                                isSimulationDashboardOpen = false
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, if (state.isDark) Color(0x26FFFFFF) else Color(0x1F423300)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_reset_simulation")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Sync from System specifications",
                                                tint = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Reset Sync",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (permissionState.allPermissionsGranted) {
                                                    viewModel.fetchGpsLocation(context)
                                                    isSimulationDashboardOpen = false
                                                } else {
                                                    permissionState.launchMultiplePermissionRequest()
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300),
                                                contentColor = if (state.isDark) Color(0xFF0F0F1A) else Color(0xFFFDF1B8)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_trigger_gps")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.GpsFixed,
                                                contentDescription = "Check device GPS coordinates",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Trigger GPS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Immersive Center state display
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(192.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Sun/Moon dynamic organic glowing halo pulsing backdrop wrapper
                                    val rippleScale by rememberInfiniteTransition(label = "haloScale").animateFloat(
                                        initialValue = 1.0f,
                                        targetValue = 1.15f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1800, easing = EaseInOutSine),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "haloAnimationScale"
                                    )

                                    val centerPulseColor = if (state.isDark) Color(0x22FFFFFF) else Color(0x40FFFFFF)
                                    val outerPulseColor = if (state.isDark) Color(0x05FFFFFF) else Color(0x0CFFFFFF)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .scale(rippleScale)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(centerPulseColor, outerPulseColor, Color.Transparent),
                                                    radius = with(LocalDensity.current) { 96.dp.toPx() }
                                                )
                                            )
                                    )

                                    val mainIconImage = if (state.isDark) Icons.Default.Bedtime else Icons.Default.WbSunny
                                    val iconActiveColor = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFFFECC00)

                                    Icon(
                                        imageVector = mainIconImage,
                                        contentDescription = state.statusText,
                                        tint = iconActiveColor,
                                        modifier = Modifier.size(118.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(28.dp))

                                // Dynamic Display State Text (Super easy to read!)
                                Text(
                                    text = state.statusText,
                                    fontSize = 62.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300),
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .testTag("sky_darkness_status_text")
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = state.statusReason.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isDark) Color(0x7FFFFFFF) else Color(0x8C423300),
                                    letterSpacing = 1.8.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )

                                // Indication tag for simulated adjustments
                                if (state.isSimulationMode) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    val simH = state.simulatedHour.toInt()
                                    val simM = ((state.simulatedHour - simH) * 60).roundToInt()
                                    val amPmIndicator = if (simH >= 12) "PM" else "AM"
                                    val formattedSimTime = String.format("%02d:%02d %s", if (simH % 12 == 0) 12 else simH % 12, simM, amPmIndicator)

                                    Surface(
                                        color = if (state.isDark) Color(0x1AFFF9DF) else Color(0x19423300),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            text = "SIM TIME: $formattedSimTime",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                            letterSpacing = 1.2.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Footer Card Section (Sized perfectly to host sunrise, sunset times, and Drag handle visual detail)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
                        )
                        .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                        .background(if (state.isDark) Color(0xD90A0915) else Color(0xD9FFFFFF))
                        .border(
                            width = 1.dp,
                            color = if (state.isDark) Color(0x1FFFFFFF) else Color(0x28423300),
                            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
                        )
                        .padding(top = 28.dp, bottom = 20.dp, start = 28.dp, end = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Sunrise and Sunset data cards side by side
                            Column(
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // Sunrise block
                                Column {
                                    Text(
                                        text = "SUNRISE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isDark) Color(0x7FFFFFFF) else Color(0x7F423300),
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LightMode,
                                            contentDescription = "Sunrise dynamic symbol icon",
                                            tint = Color(0xFFF9A825),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = state.sunriseText,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Light,
                                            letterSpacing = (-0.5).sp,
                                            color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300)
                                        )
                                    }
                                }

                                // Sunset block
                                Column {
                                    Text(
                                        text = "SUNSET",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isDark) Color(0x7FFFFFFF) else Color(0x7F423300),
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bedtime,
                                            contentDescription = "Sunset dynamic symbol icon",
                                            tint = Color(0xFF3F51B5),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = state.sunsetText,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Light,
                                            letterSpacing = (-0.5).sp,
                                            color = if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300)
                                        )
                                    }
                                }
                            }

                            // Dynamic location source badge and verification state
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val textPillSource = when (state.locationSourceType) {
                                    LocationSourceType.GPS -> "Source: GPS"
                                    LocationSourceType.TIMEZONE -> "Source: Timezone"
                                    LocationSourceType.PRESET -> "Source: Preset"
                                    LocationSourceType.CUSTOM -> "Source: Custom"
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (state.isDark) Color(0xFFFDF1B8) else Color(0xFF423300))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = textPillSource.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (state.isDark) Color(0xFF0F0F1A) else Color(0xFFFDF1B8),
                                        letterSpacing = 1.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "LAST SYNC",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isDark) Color(0x55FFFFFF) else Color(0x55423300),
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = if (state.isSimulationMode) "Simulation Active" else "Real-time Verified",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isDark) Color(0xBBFFFFFF) else Color(0xBB423300)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Aesthetic capsule bar for system navigation visual completion
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 64.dp, height = 5.dp)
                                .clip(CircleShape)
                                .background(if (state.isDark) Color(0x1BFFFFFF) else Color(0x1F423300))
                        )
                    }
                }
            }
        }
    }
}
