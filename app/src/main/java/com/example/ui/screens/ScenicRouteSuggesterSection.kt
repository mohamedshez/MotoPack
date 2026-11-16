package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ScenicRoutePreferences
import com.example.ai.ScenicRouteRecommendation
import com.example.ai.ScenicRouteUiState
import com.example.ai.ScenicStylePreference
import com.example.ui.theme.TelemetryMonoFontFamily
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenicRouteSuggesterCard(
    preferences: ScenicRoutePreferences,
    scenicState: ScenicRouteUiState,
    isEffectiveOffline: Boolean,
    onUpdatePreferences: (ScenicRoutePreferences) -> Unit,
    onSuggestRoutes: () -> Unit,
    onCancelSuggestRoutes: () -> Unit = {},
    onApplyRecommendedRoute: (ScenicRouteRecommendation) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("scenic_route_suggester_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Scenic Route Suggester",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Scenic Route Suggester",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Analyzes local traffic, live weather & riding style",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScenicStylePreference.entries) { style ->
                    FilterChip(
                        selected = preferences.style == style,
                        onClick = { onUpdatePreferences(preferences.copy(style = style)) },
                        label = { Text(style.label) },
                        modifier = Modifier.testTag("scenic_style_${style.name.lowercase()}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Range:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(45, 90, 150, 220).forEach { km ->
                    FilterChip(
                        selected = preferences.maxDistanceKm == km,
                        onClick = { onUpdatePreferences(preferences.copy(maxDistanceKm = km)) },
                        label = { Text("${km}km") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Avoid Motorways & Expressways", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = preferences.avoidMotorways,
                    onCheckedChange = { onUpdatePreferences(preferences.copy(avoidMotorways = it)) }
                )
            }

            Button(
                onClick = onSuggestRoutes,
                enabled = scenicState !is ScenicRouteUiState.Loading && !isEffectiveOffline,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("suggest_scenic_routes_button")
            ) {
                if (scenicState is ScenicRouteUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing Roads, Weather & Traffic...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEffectiveOffline) "Go Online for AI Scenic Suggestions" else "Find Best Riding Roads Now",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (scenicState is ScenicRouteUiState.Loading) {
                OutlinedButton(
                    onClick = onCancelSuggestRoutes,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_scenic_routes_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel route analysis",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Analysis")
                }
            }

            when (scenicState) {
                is ScenicRouteUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scenicState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                is ScenicRouteUiState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scenicState.bundle.analysisSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    scenicState.bundle.recommendations.forEachIndexed { idx, rec ->
                        val weatherColor = when (rec.weatherFit) {
                            "DRY_OPTIMAL" -> MaterialTheme.colorScheme.secondary
                            "CAUTION_WET" -> MaterialTheme.colorScheme.error
                            else -> Color(0xFFFFB300)
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, weatherColor.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scenic_recommendation_card_$idx")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rec.routeName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = String.format("%.0f km • %dm", rec.distanceKm, rec.estimatedMinutes),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = TelemetryMonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Curves: ${rec.curvinessRating}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = weatherColor.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(12.dp), tint = weatherColor)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = rec.weatherFit.replace("_", " "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = weatherColor
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Traffic, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = rec.trafficOutlook,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = "${rec.roadHighlights} — ${rec.whyRecommended}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocalCafe, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Pit Stop: ${rec.brewOrFuelStop}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Button(
                                    onClick = { onApplyRecommendedRoute(rec) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("plan_scenic_route_button_$idx")
                                ) {
                                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load & Preview Route (${LatLng(rec.destinationLat, rec.destinationLng).let { String.format("%.2f, %.2f", it.latitude, it.longitude) }})")
                                }
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}
