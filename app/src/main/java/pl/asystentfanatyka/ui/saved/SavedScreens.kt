package pl.asystentfanatyka.ui.saved

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.asystentfanatyka.camera.PhotoStorage
import pl.asystentfanatyka.container
import pl.asystentfanatyka.core.rules.MeasurementMethod
import pl.asystentfanatyka.core.rules.Verdict
import pl.asystentfanatyka.data.db.CatchRecordEntity
import pl.asystentfanatyka.data.db.MeasurementResultEntity
import pl.asystentfanatyka.ui.components.PL
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.formatCm
import pl.asystentfanatyka.ui.components.pl
import pl.asystentfanatyka.ui.theme.verdictColors
import java.time.LocalDate

@Composable
fun SavedScreen(onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val items by c.catches.all.collectAsStateWithLifecycle(initialValue = emptyList())
    if (items.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Brak zapisanych połowów", style = MaterialTheme.typography.titleLarge)
            Text("Po kontroli przepisów naciśnij „Zapisz”, aby zachować wynik wraz z datą, pomiarem i źródłami.")
        }
        return
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(items, key = { it.id }) { rec ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(rec.id) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerdictBadge(rec.verdict)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(rec.speciesName, style = MaterialTheme.typography.titleMedium)
                    Text(LocalDate.parse(rec.catchDate).pl(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun VerdictBadge(verdict: String) {
    val v = runCatching { Verdict.valueOf(verdict) }.getOrDefault(Verdict.CANNOT_CONFIRM)
    val col = verdictColors(v)
    Box(
        Modifier.size(44.dp).background(col.container, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) { Text(v.code.toString(), color = col.content, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) }
}

@Composable
fun SavedDetailScreen(id: Long, onDeleted: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var rec by remember { mutableStateOf<CatchRecordEntity?>(null) }
    var meas by remember { mutableStateOf<MeasurementResultEntity?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        rec = c.catches.byId(id)
        meas = c.catches.measurement(id)
        rec?.photoPath?.let { p -> photo = withContext(Dispatchers.IO) { PhotoStorage.loadBitmap(p, 1600) } }
    }
    val r = rec ?: return
    val v = runCatching { Verdict.valueOf(r.verdict) }.getOrDefault(Verdict.CANNOT_CONFIRM)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        photo?.let { Image(it.asImageBitmap(), "Zdjęcie połowu", Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerdictBadge(r.verdict)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(r.speciesName, style = MaterialTheme.typography.headlineMedium)
                Text("${v.title} · ${LocalDate.parse(r.catchDate).pl()}")
            }
        }
        SectionCard("Uzasadnienie") { Text(r.justification) }
        SectionCard("Dane kontroli") {
            Text("Gatunek potwierdzony przez wędkarza: " + if (r.speciesConfirmed) "tak" else "nie")
            meas?.let { m ->
                val method = runCatching { MeasurementMethod.valueOf(m.method).label }.getOrDefault(m.method)
                Text("Długość: ${formatCm(m.lengthCm)} ± ${String.format(PL, "%.1f", m.uncertaintyCm)} cm ($method)")
            } ?: Text("Długość: brak pomiaru")
            Text("Wymagany wymiar: ${r.requiredMin}")
            Text("Okres ochronny: ${r.closedSeason}")
            Text("Obszar: ${r.areaDescription}")
            Text("Dane przepisów: wersja ${r.datasetVersion}, zweryfikowane ${LocalDate.parse(r.datasetVerifiedOn).pl()}")
        }
        if (r.checked.isNotBlank()) SectionCard("Sprawdzone warunki") { r.checked.lines().forEach { Text(it) } }
        if (r.notVerified.isNotBlank()) SectionCard("Nie zweryfikowano") { r.notVerified.lines().forEach { Text("• $it") } }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Usuń wpis") }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Usunąć wpis?") },
        text = { Text("Wpis zostanie usunięty z telefonu. Zdjęcie także zostanie usunięte.") },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    r.photoPath?.let { java.io.File(it).delete() }
                    c.catches.delete(r.id)
                    confirmDelete = false
                    onDeleted()
                }
            }) { Text("Usuń") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } }
    )
}
