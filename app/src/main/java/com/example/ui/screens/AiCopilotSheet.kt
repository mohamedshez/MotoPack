package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AiBriefingUiState
import com.example.ai.AiCopilotUiState
import com.example.data.remote.RiderLiveWeatherAlert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCopilotBottomSheet(
    weatherAlert: RiderLiveWeatherAlert?,
    briefingState: AiBriefingUiState,
    copilotState: AiCopilotUiState,
    onRunBriefing: () -> Unit,
    onAskQuestion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var questionInput by remember { mutableStateOf("") }
    val quickPrompts = remember {
        listOf(
            "Rain & crosswind riding tips for current weather",
            "Check our convoy separation & battery plan",
            "Fuel stop & mountain pass strategy",
            "Should we switch to staggered formation?"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text("MotoPack AI Copilot", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Live weather, terrain & convoy analysis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (weatherAlert != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Live Weather Context (${weatherAlert.providerLabel}): ${weatherAlert.alertHeadline}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Structured AI Convoy Briefing Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Pre-Ride Safety Briefing", style = MaterialTheme.typography.titleSmall)
                        }
                        Button(
                            onClick = onRunBriefing,
                            enabled = briefingState !is AiBriefingUiState.Loading,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("run_ai_briefing_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (briefingState is AiBriefingUiState.Loading) "Analyzing..." else "Analyze")
                        }
                    }

                    when (briefingState) {
                        is AiBriefingUiState.Idle -> {
                            Text(
                                text = "Tap Analyze to generate a structured tactical risk briefing from live OpenWeatherMap wind/rain data, rider separation, and route elevation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiBriefingUiState.Loading -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is AiBriefingUiState.Success -> {
                            val b = briefingState.briefing
                            val riskColor = when (b.riskLevel) {
                                "HIGH" -> MaterialTheme.colorScheme.error
                                "MODERATE" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = riskColor.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, riskColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "RISK LEVEL: ${b.riskLevel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = riskColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = b.headline,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            b.tacticalAdvice.forEach { tip ->
                                Text(
                                    text = "• $tip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Action: ${b.suggestedAction}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        is AiBriefingUiState.Error -> {
                            AiErrorBanner(briefingState.message)
                        }
                    }
                }
            }

            // Quick Prompt Chips
            Text("Ask Ride Copilot", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickPrompts) { prompt ->
                    FilterChip(
                        selected = false,
                        onClick = { onAskQuestion(prompt) },
                        label = { Text(prompt, maxLines = 1) }
                    )
                }
            }

            // Streaming Response Card
            when (copilotState) {
                is AiCopilotUiState.Idle -> Unit
                is AiCopilotUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Copilot is evaluating pack & weather conditions...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is AiCopilotUiState.Streaming -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = copilotState.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is AiCopilotUiState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_copilot_response")
                    ) {
                        Text(
                            text = copilotState.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is AiCopilotUiState.Error -> {
                    AiErrorBanner(copilotState.message)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = questionInput,
                    onValueChange = { questionInput = it },
                    placeholder = { Text("Ask about rain, crosswinds, fuel or route...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_copilot_input")
                )
                IconButton(
                    onClick = {
                        if (questionInput.isNotBlank()) {
                            onAskQuestion(questionInput)
                            questionInput = ""
                        }
                    },
                    enabled = copilotState !is AiCopilotUiState.Loading,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("ai_copilot_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Ask MotoPack AI Copilot",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AiErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "AI Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
