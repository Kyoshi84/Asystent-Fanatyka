package pl.asystentfanatyka.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Czytelny dziennik zdarzeń: wpisy trafiają do Logcat (tag „AsystentFanatyka”) i do pamięci,
 * skąd można je obejrzeć w Ustawieniach. Dziennik nie zawiera zdjęć ani danych osobowych.
 */
object AppLog {
    private const val TAG = "AsystentFanatyka"
    private const val MAX = 100
    private val fmt = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")

    data class Entry(val time: String, val level: String, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun i(message: String) {
        Log.i(TAG, message)
        add("INFO", message)
    }

    fun w(message: String, t: Throwable? = null) {
        Log.w(TAG, message, t)
        add("UWAGA", message + (t?.let { ": ${it.javaClass.simpleName} ${it.message}" } ?: ""))
    }

    fun e(message: String, t: Throwable? = null) {
        Log.e(TAG, message, t)
        add("BŁĄD", message + (t?.let { ": ${it.javaClass.simpleName} ${it.message}" } ?: ""))
    }

    private fun add(level: String, message: String) {
        _entries.value = (listOf(Entry(LocalDateTime.now().format(fmt), level, message)) + _entries.value).take(MAX)
    }
}
