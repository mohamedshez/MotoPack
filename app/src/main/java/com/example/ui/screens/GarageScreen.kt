package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditRoad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GarageBike
import com.example.data.model.GarageMaintenanceCalculator
import com.example.data.model.MaintenanceHealthStatus
import com.example.data.model.MaintenanceReminderItem
import com.example.data.model.MaintenanceTaskType
import com.example.data.model.ServiceHistoryLog
import com.example.ui.theme.TelemetryMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GarageScreen(
    bikes: List<GarageBike>,
    reminders: List<MaintenanceReminderItem>,
    serviceLogs: List<ServiceHistoryLog>,
    defaultBikeName: String,
    onAddBike: (
        name: String,
        makeModel: String,
        year: Int,
        odometerKm: Double,
        engineCc: Int,
        tirePsiFront: Int,
        tirePsiRear: Int
    ) -> Unit,
    onUpdateOdometer: (bikeId: String, newOdometerKm: Double) -> Unit,
    onAddReminder: (
        bike: GarageBike,
        taskType: MaintenanceTaskType,
        title: String,
        intervalKm: Double,
        notes: String
    ) -> Unit,
    onLogServiceDone: (
        bike: GarageBike,
        reminder: MaintenanceReminderItem?,
        taskType: MaintenanceTaskType,
        title: String,
        odometerKm: Double,
        costAmount: Double,
        shopOrDiy: String,
        notes: String
    ) -> Unit,
    onDeleteReminder: (itemId: String) -> Unit,
    onDeleteServiceLog: (logId: String) -> Unit
) {
    var selectedBikeId by rememberSaveable { mutableStateOf("") }
    var selectedSectionTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddBikeDialog by remember { mutableStateOf(false) }
    var showOdometerDialogFor by remember { mutableStateOf<GarageBike?>(null) }
    var showAddReminderFor by remember { mutableStateOf<GarageBike?>(null) }
    var serviceLogTarget by remember { mutableStateOf<Pair<GarageBike, MaintenanceReminderItem?>?>(null) }

    val selectedBike = remember(bikes, selectedBikeId) {
        bikes.firstOrNull { it.id == selectedBikeId } ?: bikes.firstOrNull()
    }
    val bikeReminders = remember(reminders, selectedBike) {
        if (selectedBike == null) emptyList()
        else reminders.filter { it.bikeId == selectedBike.id }
            .map { GarageMaintenanceCalculator.evaluate(it, selectedBike.currentOdometerKm) }
            .sortedBy { it.remainingKm }
    }
    val bikeLogs = remember(serviceLogs, selectedBike) {
        if (selectedBike == null) emptyList()
        else serviceLogs.filter { it.bikeId == selectedBike.id }
    }
    val overdueCount = remember(bikeReminders) {
        bikeReminders.count { it.status == MaintenanceHealthStatus.OVERDUE }
    }
    val dueSoonCount = remember(bikeReminders) {
        bikeReminders.count { it.status == MaintenanceHealthStatus.DUE_SOON }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("My Moto Garage", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = if (selectedBike != null) {
                            "${bikes.size} bike(s) • $overdueCount overdue, $dueSoonCount due soon"
                        } else {
                            "Track maintenance schedules, odometer & service history"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                }
                Button(
                    onClick = { showAddBikeDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("garage_add_bike_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Bike")
                }
            }
        }

        if (bikes.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garage_empty_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "Add Your Motorcycle to the Garage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Track engine oil changes, chain tension/lube, tires, valve checks, and service receipts automatically as you log rides.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showAddBikeDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("garage_setup_first_bike_button")
                        ) {
                            Text("Setup First Motorcycle")
                        }
                    }
                }
            }
        } else if (selectedBike != null) {
            if (bikes.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bikes, key = { it.id }) { bike ->
                            FilterChip(
                                selected = bike.id == selectedBike.id,
                                onClick = { selectedBikeId = bike.id },
                                label = { Text("${bike.year} ${bike.name}") }
                            )
                        }
                    }
                }
            }

            // Active Bike Telemetry Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garage_active_bike_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${selectedBike.year} ${selectedBike.makeModel}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${selectedBike.engineCc} cc • Cold Tire PSI ${selectedBike.tirePsiFront}F / ${selectedBike.tirePsiRear}R",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${selectedBike.currentOdometerKm.toInt()} km",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = TelemetryMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onUpdateOdometer(selectedBike.id, selectedBike.currentOdometerKm + 50.0) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("garage_add_50km_button")
                            ) {
                                Text("+50 km")
                            }
                            OutlinedButton(
                                onClick = { showOdometerDialogFor = selectedBike },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("garage_edit_odometer_button")
                            ) {
                                Icon(Icons.Default.EditRoad, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Set Odo")
                            }
                            Button(
                                onClick = { showAddReminderFor = selectedBike },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("garage_add_reminder_button")
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reminder")
                            }
                        }
                    }
                }
            }

            // 2-Tab Switcher: Maintenance vs Service History
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedSectionTab == 0,
                        onClick = { selectedSectionTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("garage_tab_maintenance")
                    ) {
                        Text("Maintenance (${bikeReminders.size})", maxLines = 1)
                    }
                    SegmentedButton(
                        selected = selectedSectionTab == 1,
                        onClick = { selectedSectionTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("garage_tab_history")
                    ) {
                        Text("Service History (${bikeLogs.size})", maxLines = 1)
                    }
                }
            }

            if (selectedSectionTab == 0) {
                // Mileage-Based Maintenance Reminders
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mileage Maintenance (${bikeReminders.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = { serviceLogTarget = selectedBike to null },
                            modifier = Modifier.testTag("garage_log_adhoc_service_button")
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Custom")
                        }
                    }
                }

            if (bikeReminders.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No reminders for ${selectedBike.name} yet. Tap 'Reminder' to schedule oil changes, chain tension, or tire checks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(bikeReminders, key = { it.item.id }) { computed ->
                    val statusColor = when (computed.status) {
                        MaintenanceHealthStatus.OVERDUE -> MaterialTheme.colorScheme.error
                        MaintenanceHealthStatus.DUE_SOON -> Color(0xFFFFB300)
                        MaintenanceHealthStatus.GOOD -> MaterialTheme.colorScheme.secondary
                    }
                    val statusLabel = when (computed.status) {
                        MaintenanceHealthStatus.OVERDUE -> "OVERDUE by ${(-computed.remainingKm).toInt()} km"
                        MaintenanceHealthStatus.DUE_SOON -> "DUE SOON • ${computed.remainingKm.toInt()} km left"
                        MaintenanceHealthStatus.GOOD -> "${computed.remainingKm.toInt()} km remaining"
                    }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("maintenance_card_${computed.item.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (computed.status == MaintenanceHealthStatus.OVERDUE) {
                                            Icons.Default.Warning
                                        } else {
                                            Icons.Default.Build
                                        },
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = computed.item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = TelemetryMonoFontFamily,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteReminder(computed.item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete reminder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { computed.progressFraction },
                                color = statusColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Every ${computed.item.intervalKm.toInt()} km • Next at ${computed.item.nextDueOdometerKm.toInt()} km",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { serviceLogTarget = selectedBike to computed.item },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("complete_service_${computed.item.id}")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Log Service Done")
                                }
                            }

                            if (computed.item.notes.isNotBlank()) {
                                Text(
                                    text = computed.item.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            } else {
                // Service History Ledger
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Service History (${bikeLogs.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(
                            onClick = { serviceLogTarget = selectedBike to null },
                            modifier = Modifier.testTag("garage_log_adhoc_service_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Service")
                        }
                    }
                }

            if (bikeLogs.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No completed services recorded yet. Mark any maintenance reminder done to archive receipts, shop notes, and exact odometer mileage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                items(bikeLogs, key = { it.id }) { log ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("service_log_row_${log.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(log.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${log.odometerKm.toInt()} km • ${dateFmt.format(Date(log.performedAtMs))} • ${log.shopOrDiy}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = TelemetryMonoFontFamily,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (log.notes.isNotBlank()) {
                                    Text(
                                        text = log.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (log.costAmount > 0.0) {
                                    Text(
                                        text = String.format("$%.2f", log.costAmount),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeleteServiceLog(log.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete service log",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    if (showAddBikeDialog) {
        var name by remember { mutableStateOf(defaultBikeName.ifBlank { "Tracer 9" }) }
        var makeModel by remember { mutableStateOf("Yamaha Tracer 9 GT") }
        var yearStr by remember { mutableStateOf("2024") }
        var odoStr by remember { mutableStateOf("12400") }
        var ccStr by remember { mutableStateOf("890") }
        var psiFStr by remember { mutableStateOf("36") }
        var psiRStr by remember { mutableStateOf("42") }

        AlertDialog(
            onDismissRequest = { showAddBikeDialog = false },
            title = { Text("Add Motorcycle to Garage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nickname") }, singleLine = true)
                    OutlinedTextField(value = makeModel, onValueChange = { makeModel = it }, label = { Text("Make & Model") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = yearStr,
                            onValueChange = { yearStr = it.filter(Char::isDigit) },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ccStr,
                            onValueChange = { ccStr = it.filter(Char::isDigit) },
                            label = { Text("Engine cc") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = odoStr,
                        onValueChange = { odoStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Current Odometer (km)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = psiFStr,
                            onValueChange = { psiFStr = it.filter(Char::isDigit) },
                            label = { Text("Front PSI") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = psiRStr,
                            onValueChange = { psiRStr = it.filter(Char::isDigit) },
                            label = { Text("Rear PSI") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddBike(
                            name,
                            makeModel,
                            yearStr.toIntOrNull() ?: 2024,
                            odoStr.toDoubleOrNull() ?: 0.0,
                            ccStr.toIntOrNull() ?: 890,
                            psiFStr.toIntOrNull() ?: 36,
                            psiRStr.toIntOrNull() ?: 42
                        )
                        showAddBikeDialog = false
                    },
                    modifier = Modifier.testTag("confirm_add_bike_button")
                ) {
                    Text("Save Bike")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBikeDialog = false }) { Text("Cancel") }
            }
        )
    }

    showOdometerDialogFor?.let { bike ->
        var odoStr by remember { mutableStateOf(bike.currentOdometerKm.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showOdometerDialogFor = null },
            title = { Text("Update ${bike.name} Odometer") },
            text = {
                OutlinedTextField(
                    value = odoStr,
                    onValueChange = { odoStr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Odometer Reading (km)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val km = odoStr.toDoubleOrNull()
                        if (km != null && km >= 0.0) {
                            onUpdateOdometer(bike.id, km)
                            showOdometerDialogFor = null
                        }
                    }
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showOdometerDialogFor = null }) { Text("Cancel") }
            }
        )
    }

    showAddReminderFor?.let { bike ->
        var selectedType by remember { mutableStateOf(MaintenanceTaskType.OIL_CHANGE) }
        var title by remember { mutableStateOf(selectedType.defaultTitle) }
        var intervalStr by remember { mutableStateOf(selectedType.defaultIntervalKm.toInt().toString()) }
        var notes by remember { mutableStateOf(selectedType.checklistHint) }

        AlertDialog(
            onDismissRequest = { showAddReminderFor = null },
            title = { Text("New Mileage Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MaintenanceTaskType.entries) { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    title = type.defaultTitle
                                    intervalStr = type.defaultIntervalKm.toInt().toString()
                                    notes = type.checklistHint
                                },
                                label = { Text(type.name.replace("_", " ")) }
                            )
                        }
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Name") }, singleLine = true)
                    OutlinedTextField(
                        value = intervalStr,
                        onValueChange = { intervalStr = it.filter(Char::isDigit) },
                        label = { Text("Interval (km)") },
                        singleLine = true
                    )
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Checklist Notes") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val interval = intervalStr.toDoubleOrNull()?.coerceAtLeast(50.0) ?: selectedType.defaultIntervalKm
                        onAddReminder(bike, selectedType, title, interval, notes)
                        showAddReminderFor = null
                    },
                    modifier = Modifier.testTag("confirm_add_reminder_button")
                ) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminderFor = null }) { Text("Cancel") }
            }
        )
    }

    serviceLogTarget?.let { (bike, reminder) ->
        val taskType = reminder?.let { MaintenanceTaskType.fromCode(it.taskType) } ?: MaintenanceTaskType.CUSTOM
        var title by remember { mutableStateOf(reminder?.title ?: taskType.defaultTitle) }
        var odoStr by remember { mutableStateOf(bike.currentOdometerKm.toInt().toString()) }
        var costStr by remember { mutableStateOf("45.00") }
        var shopOrDiy by remember { mutableStateOf("DIY Garage") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { serviceLogTarget = null },
            title = { Text("Log Service History") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Service Work") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = odoStr,
                            onValueChange = { odoStr = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Odometer (km)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = costStr,
                            onValueChange = { costStr = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Cost ($)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(value = shopOrDiy, onValueChange = { shopOrDiy = it }, label = { Text("Workshop or DIY") }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Parts / Oil Spec / Notes") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLogServiceDone(
                            bike,
                            reminder,
                            taskType,
                            title,
                            odoStr.toDoubleOrNull() ?: bike.currentOdometerKm,
                            costStr.toDoubleOrNull() ?: 0.0,
                            shopOrDiy,
                            notes
                        )
                        serviceLogTarget = null
                    },
                    modifier = Modifier.testTag("confirm_log_service_button")
                ) { Text("Save Log") }
            },
            dismissButton = {
                TextButton(onClick = { serviceLogTarget = null }) { Text("Cancel") }
            }
        )
    }
}
