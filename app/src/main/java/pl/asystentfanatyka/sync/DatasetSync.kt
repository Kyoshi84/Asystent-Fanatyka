package pl.asystentfanatyka.sync

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.asystentfanatyka.data.AppLog
import pl.asystentfanatyka.data.ImportResult
import pl.asystentfanatyka.data.RegulationRepository
import pl.asystentfanatyka.data.SettingsRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Aktualizacja danych przepisów.
 *
 * Nie istnieje publiczne API z przepisami wędkarskimi, więc aplikacja nie zakłada żadnego serwera.
 * Obsługiwane są dwie drogi:
 *  1. import pliku JSON wskazanego przez użytkownika (np. przygotowanego skryptem tools/build_dataset.py),
 *  2. pobranie pliku JSON z adresu HTTPS ustawionego w Ustawieniach (np. własny plik w repozytorium).
 * W obu przypadkach obowiązuje ta sama walidacja i atomowa podmiana danych.
 */
class DatasetSync(
    private val repository: RegulationRepository,
    private val settings: SettingsRepository
) {
    suspend fun importFromUri(resolver: ContentResolver, uri: Uri, allowDemo: Boolean): ImportResult = withContext(Dispatchers.IO) {
        try {
            val text = resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) return@withContext ImportResult.Failed(listOf("Plik jest zbyt duży (limit ${MAX_BYTES / 1024 / 1024} MB)."))
                String(bytes, Charsets.UTF_8)
            } ?: return@withContext ImportResult.Failed(listOf("Nie można otworzyć pliku."))
            val r = repository.import(text, origin = "file", requireNewer = false, allowDemo = allowDemo)
            settings.setLastSync(System.currentTimeMillis(), describe(r, "Import z pliku"))
            r
        } catch (t: Throwable) {
            AppLog.e("Import pliku nie powiódł się", t)
            ImportResult.Failed(listOf("Błąd odczytu pliku: ${t.message}"))
        }
    }

    suspend fun checkForUpdate(): ImportResult = withContext(Dispatchers.IO) {
        val url = settings.current().updateUrl
        if (url.isBlank()) return@withContext ImportResult.Skipped("Nie ustawiono adresu aktualizacji.")
        if (!url.startsWith("https://")) return@withContext ImportResult.Failed(listOf("Adres aktualizacji musi zaczynać się od https://"))
        val result = try {
            val text = download(url)
            repository.import(text, origin = "url:$url", requireNewer = true)
        } catch (e: IOException) {
            AppLog.w("Brak połączenia lub błąd serwera przy aktualizacji danych", e)
            ImportResult.Failed(listOf("Nie udało się pobrać danych: ${e.message ?: "brak połączenia"}. Używane są ostatnie zapisane dane."))
        } catch (t: Throwable) {
            AppLog.e("Nieoczekiwany błąd aktualizacji danych", t)
            ImportResult.Failed(listOf("Nieoczekiwany błąd: ${t.message}. Używane są ostatnie zapisane dane."))
        }
        settings.setLastSync(System.currentTimeMillis(), describe(result, "Aktualizacja z sieci"))
        result
    }

    /** Sprawdza aktualizacje najwyżej raz na dobę, jeśli ustawiono adres. Błędy są tylko logowane. */
    suspend fun checkIfDue() {
        val s = settings.current()
        if (s.updateUrl.isBlank()) return
        if (System.currentTimeMillis() - s.lastSyncMillis < DAY_MS) return
        checkForUpdate()
    }

    private fun download(url: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val len = conn.contentLengthLong
            if (len > MAX_BYTES) throw IOException("plik zbyt duży ($len B)")
            val bytes = conn.inputStream.use { it.readNBytesCompat(MAX_BYTES + 1) }
            if (bytes.size > MAX_BYTES) throw IOException("plik zbyt duży")
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    private fun describe(r: ImportResult, what: String) = when (r) {
        is ImportResult.Imported -> "$what: zainstalowano wersję ${r.version}" + if (r.warnings.isNotEmpty()) " (ostrzeżenia: ${r.warnings.size})" else ""
        is ImportResult.Skipped -> "$what: ${r.reason}"
        is ImportResult.Failed -> "$what nieudany: ${r.errors.first()}"
    }

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
