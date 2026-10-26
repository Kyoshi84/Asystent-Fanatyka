package pl.asystentfanatyka.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.asystentfanatyka.BuildConfig
import pl.asystentfanatyka.container
import pl.asystentfanatyka.data.AppLog
import pl.asystentfanatyka.data.AppSettings
import pl.asystentfanatyka.data.DatasetState
import pl.asystentfanatyka.data.ImportResult
import pl.asystentfanatyka.recognition.RecognizerStatus
import pl.asystentfanatyka.ui.components.AreaSelector
import pl.asystentfanatyka.ui.components.DataFreshnessBanner
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.WarningBox
import pl.asystentfanatyka.ui.components.pl

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val c = ctx.container
    val state by c.regulations.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val log by AppLog.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var allowDemo by remember { mutableStateOf(false) }
    var urlText by remember(settings.updateUrl) { mutableStateOf(settings.updateUrl) }
    var busy by remember { mutableStateOf(false) }

    fun describe(r: ImportResult) = when (r) {
        is ImportResult.Imported -> "Zainstalowano dane w wersji ${r.version}." + if (r.warnings.isNotEmpty()) " Ostrzeżenia: ${r.warnings.take(3).joinToString("; ")}" else ""
        is ImportResult.Skipped -> r.reason
        is ImportResult.Failed -> "Nie zaimportowano – poprzednie dane pozostają aktywne. Błędy: " + r.errors.take(5).joinToString("; ")
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = describe(c.sync.importFromUri(ctx.contentResolver, uri, allowDemo))
            busy = false
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        message?.let { WarningBox(it) }

        SectionCard("Dane o przepisach") {
            when (val s = state) {
                DatasetState.Loading -> Text("Wczytywanie…")
                is DatasetState.Error -> WarningBox(s.message)
                is DatasetState.Ready -> {
                    val m = s.dataset.metadata
                    DataFreshnessBanner(s.dataset, settings.stalenessDays)
                    Text("Zbiór: ${m.datasetId}, wersja ${m.version}")
                    Text("Zweryfikowano: ${m.verifiedOn.pl()} · obejmuje przepisy od ${m.coverageFrom.pl()}")
                    Text("Pochodzenie: " + when {
                        s.origin == "asset" -> "dane dołączone do aplikacji"
                        s.origin == "file" -> "import z pliku"
                        s.origin.startsWith("url:") -> "pobrane z ${s.origin.removePrefix("url:")}"
                        else -> s.origin
                    })
                    Text(m.coverageNote, style = MaterialTheme.typography.bodyMedium)
                    Text("Gatunki: ${s.dataset.species.size} · reguły: ${s.dataset.rules.size}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Uznawaj dane za nieaktualne po:", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(90, 180, 365).forEach { d ->
                    FilterChip(selected = settings.stalenessDays == d, onClick = { scope.launch { c.settings.setStalenessDays(d) } }, label = { Text("$d dniach") })
                }
            }
        }

        SectionCard("Aktualizacja danych") {
            Text(
                "Nie istnieje publiczne API z przepisami wędkarskimi. Dane można zaktualizować z pliku JSON " +
                    "(format: docs/FORMAT_DANYCH.md) albo z własnego adresu HTTPS. Plik z błędami jest odrzucany w całości.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                enabled = !busy,
                onClick = { fileLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Importuj plik danych (JSON)") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowDemo, onCheckedChange = { allowDemo = it })
                Text("Pozwól zastąpić dane plikiem demonstracyjnym", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = urlText, onValueChange = { urlText = it }, singleLine = true,
                label = { Text("Adres pliku aktualizacji (https://…)") }, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { c.settings.setUpdateUrl(urlText); message = "Zapisano adres." } }) { Text("Zapisz adres") }
                OutlinedButton(enabled = !busy && settings.updateUrl.isNotBlank(), onClick = {
                    busy = true
                    scope.launch { message = describe(c.sync.checkForUpdate()); busy = false }
                }) { Text("Sprawdź teraz") }
            }
            if (settings.lastSyncMessage.isNotBlank()) Text("Ostatnio: ${settings.lastSyncMessage}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                scope.launch { message = if (c.regulations.rollbackToPrevious()) "Przywrócono poprzednią wersję danych." else "Brak poprzedniej wersji do przywrócenia." }
            }) { Text("Przywróć poprzednią wersję danych") }
        }

        (state as? DatasetState.Ready)?.let { s ->
            SectionCard("Domyślny obszar połowu") {
                AreaSelector(s.dataset.areas, settings.areaContext) { a ->
                    scope.launch {
                        when (a) {
                            pl.asystentfanatyka.core.rules.AreaContext.Unknown -> c.settings.setArea(false, emptySet())
                            is pl.asystentfanatyka.core.rules.AreaContext.Known -> c.settings.setArea(true, a.areaCodes)
                        }
                    }
                }
            }
            SectionCard("Źródła danych") {
                s.dataset.sources.forEach { src ->
                    Text(src.title, fontWeight = FontWeight.SemiBold)
                    Text(src.reference + (src.verifiedOn?.let { " · zweryfikowano ${it.pl()}" } ?: " · nie weryfikowano"), style = MaterialTheme.typography.bodySmall)
                    src.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    src.url?.let { url -> TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Otwórz") } }
                }
            }
        }

        SectionCard("Zdjęcia i prywatność") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zachowuj zdjęcia przy zapisanych połowach", Modifier.weight(1f))
                Switch(checked = settings.keepPhotos, onCheckedChange = { v -> scope.launch { c.settings.setKeepPhotos(v) } })
            }
            Text("Zdjęcia nie są wysyłane poza telefon. Kopia zapasowa systemu Android jest wyłączona dla tej aplikacji.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                scope.launch {
                    c.photos.deleteAllExcept(c.catches.photoPaths())
                    message = "Usunięto zdjęcia niepowiązane z zapisanymi połowami."
                }
            }) { Text("Usuń zbędne zdjęcia") }
            OutlinedButton(onClick = {
                scope.launch {
                    c.photos.deleteAll(); c.catches.clearPhotoPaths()
                    message = "Usunięto wszystkie zdjęcia."
                }
            }) { Text("Usuń wszystkie zdjęcia") }
        }

        SectionCard("Rozpoznawanie AI") {
            when (val st = c.recognizer.status) {
                RecognizerStatus.Ready -> Text("${c.recognizer.displayName}: aktywny" + if (c.recognizer.sendsPhotoOffDevice) " (wysyła zdjęcie na serwer)" else " (działa na telefonie)")
                is RecognizerStatus.NotConfigured -> Text(st.reason)
                is RecognizerStatus.Unavailable -> Text(st.reason)
            }
            Text("O legalności połowu nigdy nie decyduje model AI – tylko silnik reguł na podstawie zweryfikowanych danych.", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Pomiar – ograniczenia") {
            Text("• Pojedyncze zdjęcie bez wzorca skali nie pozwala ustalić długości – aplikacja zawsze wymaga wzorca.")
            Text("• Nie jest wykonywana korekta perspektywy: rób zdjęcie prostopadle z góry, podziałka przy rybie.")
            Text("• Wynik w pobliżu wymiaru ochronnego (w granicach niepewności) daje stan C – zmierz na macie.")
        }

        SectionCard("Dziennik zdarzeń") {
            if (log.isEmpty()) Text("Brak wpisów.")
            log.take(20).forEach { Text("${it.time} ${it.level}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
        }

        Text(
            "Asystent Fanatyka ${BuildConfig.VERSION_NAME}. Aplikacja pomocnicza – nie zastępuje przepisów ani regulaminów. " +
                "Odpowiedzialność za przestrzeganie prawa ponosi wędkarz.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
