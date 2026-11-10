package com.example.subdash

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** >>> Pre-set to your sheet. Can be changed any time in the app Settings (gear icon). <<< */
const val DEFAULT_SHEET = "https://docs.google.com/spreadsheets/d/1dbmHjC4Mc0RLYjXI35Qab8U_OOrvDlSrdQRZ0RQxInc/edit?usp=sharing"
const val DEFAULT_TAB = ""   // leave empty for the first tab

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val entries: List<Entry> = emptyList(),
    val selected: LocalDate? = null,      // null = follow today's date
    val today: LocalDate = LocalDate.now(),
    val lastUpdated: String? = null,
    val sheetUrl: String = DEFAULT_SHEET,
    val tab: String = DEFAULT_TAB
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("config", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            sheetUrl = prefs.getString("url", DEFAULT_SHEET) ?: DEFAULT_SHEET,
            tab = prefs.getString("tab", DEFAULT_TAB) ?: DEFAULT_TAB
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Auto-refresh every minute (also keeps "today" correct after midnight).
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(60_000)
            }
        }
    }

    fun reload() { viewModelScope.launch { refresh() } }

    fun selectDate(date: LocalDate?) = _state.update { it.copy(selected = date) }

    fun saveConfig(url: String, tab: String) {
        prefs.edit().putString("url", url.trim()).putString("tab", tab.trim()).apply()
        _state.update { it.copy(sheetUrl = url.trim(), tab = tab.trim(), entries = emptyList()) }
        reload()
    }

    private suspend fun refresh() {
        val s = _state.value
        _state.update { it.copy(loading = true, today = LocalDate.now()) }
        try {
            val data = SheetRepository.fetch(s.sheetUrl, s.tab)
            _state.update {
                it.copy(
                    entries = data, error = null, loading = false,
                    lastUpdated = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "Something went wrong.") }
        }
    }
}
