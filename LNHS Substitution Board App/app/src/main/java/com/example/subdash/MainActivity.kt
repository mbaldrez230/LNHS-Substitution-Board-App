package com.example.subdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DashboardScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    val date = s.selected ?: s.today
    val dayEntries = remember(s.entries, date) {
        s.entries.filter { it.date == date }
            .sortedWith(compareBy({ it.minutes ?: Int.MAX_VALUE }, { it.section }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Substitute Dashboard") },
                actions = {
                    IconButton(onClick = { vm.reload() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }

            item {
                DateBar(
                    title = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                    isToday = date == s.today,
                    onPrev = { vm.selectDate(date.minusDays(1)) },
                    onNext = { vm.selectDate(date.plusDays(1)) },
                    onToday = { vm.selectDate(null) }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Entries", dayEntries.size.toString(), Modifier.weight(1f))
                    StatCard("Sections", dayEntries.map { it.section }.filter { it.isNotBlank() }.distinct().size.toString(), Modifier.weight(1f))
                    StatCard("Substitutes", dayEntries.count { it.substitute.isNotBlank() }.toString(), Modifier.weight(1f))
                }
            }

            s.error?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(msg, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (dayEntries.isEmpty() && s.error == null && !s.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No entries for this date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(dayEntries) { EntryCard(it) }

            s.lastUpdated?.let {
                item {
                    Text(
                        "Last updated $it · auto-refreshes every minute",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            url = s.sheetUrl, tab = s.tab,
            onDismiss = { showSettings = false },
            onSave = { u, t -> vm.saveConfig(u, t); showSettings = false }
        )
    }
}

@Composable
fun DateBar(title: String, isToday: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.KeyboardArrowLeft, "Previous day") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (isToday) Text("Today", fontSize = 12.sp)
                else TextButton(onClick = onToday) { Text("Back to today") }
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.KeyboardArrowRight, "Next day") }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            value.ifBlank { "—" },
            fontSize = if (big) 20.sp else 16.sp,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun EntryCard(e: Entry) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("NAME", e.name, big = true)
            Row(Modifier.fillMaxWidth()) {
                Field("DATE", e.date?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "", Modifier.weight(1f))
                Field("TIMESLOT", e.timeslot, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Field("SUBJECT", e.subject, Modifier.weight(1f))
                Field("SECTION", e.section, Modifier.weight(1f))
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (e.substitute.isNotBlank()) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
            ) {
                Field("SUBSTITUTE", e.substitute, Modifier.fillMaxWidth().padding(12.dp), big = true)
            }
        }
    }
}

@Composable
fun SettingsDialog(url: String, tab: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var u by remember { mutableStateOf(url) }
    var t by remember { mutableStateOf(tab) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Sheet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(u, { u = it }, label = { Text("Sheet link or ID") }, singleLine = true)
                OutlinedTextField(t, { t = it }, label = { Text("Tab name (optional)") }, singleLine = true)
                Text(
                    "Columns: NAME, DATE, TIMESLOT, SUBJECT, SECTION, SUBSTITUTE. " +
                        "Share the sheet as \"Anyone with the link – Viewer\".",
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(u, t) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
