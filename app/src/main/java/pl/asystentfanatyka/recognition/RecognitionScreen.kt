package pl.asystentfanatyka.recognition

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.asystentfanatyka.camera.PhotoStorage
import pl.asystentfanatyka.container
import pl.asystentfanatyka.core.measurement.ImageQualityReport
import pl.asystentfanatyka.core.model.RegulationDataset
import pl.asystentfanatyka.core.recognition.IdentificationKey
import pl.asystentfanatyka.core.recognition.KeyNode
import pl.asystentfanatyka.core.recognition.RecognitionCandidate
import pl.asystentfanatyka.core.recognition.RecognitionOutcome
import pl.asystentfanatyka.core.recognition.RecognitionPolicy
import pl.asystentfanatyka.core.recognition.RecognitionSource
import pl.asystentfanatyka.core.rules.SpeciesCertainty
import pl.asystentfanatyka.data.AppLog
import pl.asystentfanatyka.data.DatasetState
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.SpeciesPickerDialog
import pl.asystentfanatyka.ui.components.WarningBox
import pl.asystentfanatyka.ui.session.CatchSessionViewModel

@Composable
fun RecognitionScreen(
    vm: CatchSessionViewModel,
    onConfirmed: (speciesId: String) -> Unit,
    onRetakePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalContext.current.container
    val session by vm.session.collectAsStateWithLifecycle()
    val state by c.regulations.state.collectAsStateWithLifecycle()
    val ds = (state as? DatasetState.Ready)?.dataset ?: run { Text("Brak danych o gatunkach."); return }
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quality by remember { mutableStateOf<ImageQualityReport?>(null) }
    LaunchedEffect(session.photoPath) {
        val p = session.photoPath ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val b = PhotoStorage.loadBitmap(p, 1600)
            bitmap = b
            quality = b?.let { PhotoStorage.analyzeQuality(it) }
        }
    }

    val path = remember { mutableStateListOf<KeyNode>(IdentificationKey.root) }
    var modelOutcome by remember { mutableStateOf<RecognitionOutcome?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    fun confirm(id: String, source: RecognitionSource, candidates: List<RecognitionCandidate>) {
        vm.setRecognition(RecognitionOutcome(source, candidates, quality?.acceptable == false, null))
        vm.setSpecies(id, SpeciesCertainty.CONFIRMED)
        onConfirmed(id)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), "Zdjęcie ryby", Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
        }
        quality?.takeIf { !it.acceptable }?.let { q ->
            WarningBox("Jakość zdjęcia może utrudnić rozpoznanie: " + q.warnings.joinToString(" "))
            OutlinedButton(onClick = onRetakePhoto) { Text("Zrób lepsze zdjęcie") }
        }

        // --- Model AI ---------------------------------------------------------------------------
        SectionCard("Rozpoznawanie przez model AI") {
            when (val st = c.recognizer.status) {
                is RecognizerStatus.NotConfigured -> Text(st.reason)
                is RecognizerStatus.Unavailable -> WarningBox(st.reason)
                RecognizerStatus.Ready -> {
                    Button(
                        enabled = bitmap != null,
                        onClick = {
                            val b = bitmap ?: return@Button
                            scope.launch {
                                modelOutcome = try { c.recognizer.recognize(b) } catch (t: Throwable) {
                                    AppLog.e("Rozpoznawanie nie powiodło się", t); null
                                }
                            }
                        }
                    ) { Text("Rozpoznaj na zdjęciu") }
                    modelOutcome?.let { out -> ModelResult(out, ds) { id -> confirm(id, out.source, out.candidates) } }
                }
            }
        }

        // --- Klucz cech -------------------------------------------------------------------------
        SectionCard("Klucz cech – krok po kroku") {
            when (val node = path.last()) {
                is KeyNode.Question -> {
                    Text(node.text, style = MaterialTheme.typography.titleMedium)
                    node.hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    node.options.forEach { opt ->
                        OutlinedButton(
                            onClick = { path.add(opt.next) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        ) { Text(opt.label, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
                    }
                }
                is KeyNode.Answer -> {
                    if (node.speciesIds.size > 1) Text("Możliwe gatunki – porównaj cechy i wybierz właściwy:", style = MaterialTheme.typography.titleMedium)
                    node.note?.let { WarningBox(it) }
                    node.speciesIds.mapNotNull { ds.speciesById(it) }.forEach { sp ->
                        SpeciesCandidateCard(ds, sp.id) {
                            confirm(sp.id, RecognitionSource.IDENTIFICATION_KEY, node.speciesIds.map { RecognitionCandidate(it, null) })
                        }
                    }
                }
            }
            if (path.size > 1) {
                TextButton(onClick = { path.removeAt(path.lastIndex) }) { Text("← Poprzednie pytanie") }
                TextButton(onClick = { path.clear(); path.add(IdentificationKey.root) }) { Text("Zacznij od nowa") }
            }
        }

        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Znam gatunek – wybierz z listy")
        }
    }

    if (showPicker) SpeciesPickerDialog(ds.species, { showPicker = false }) { sp ->
        showPicker = false
        confirm(sp.id, RecognitionSource.USER_SELECTION, listOf(RecognitionCandidate(sp.id, null)))
    }
}

@Composable
private fun SpeciesCandidateCard(ds: RegulationDataset, speciesId: String, onConfirm: () -> Unit) {
    val sp = ds.speciesById(speciesId) ?: return
    SectionCard(null) {
        Text(sp.namePl, style = MaterialTheme.typography.titleLarge)
        Text(sp.nameLatin, fontStyle = FontStyle.Italic)
        Text("Sprawdź na rybie:", style = MaterialTheme.typography.titleMedium)
        sp.features.forEach { Text("• $it") }
        val similar = sp.similarSpeciesIds.mapNotNull { ds.speciesById(it)?.namePl }
        if (similar.isNotEmpty()) Text("Łatwo pomylić z: ${similar.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        if (sp.invasive) WarningBox("Gatunek inwazyjny – nie wolno go wypuścić.")
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Potwierdzam: to ${sp.namePl.lowercase()}") }
    }
}

@Composable
private fun ModelResult(out: RecognitionOutcome, ds: RegulationDataset, onConfirm: (String) -> Unit) {
    val needsConfirm = RecognitionPolicy.needsConfirmation(out, RecognitionPolicy.CONFUSABLE)
    if (out.needsBetterPhoto) WarningBox("Model prosi o lepsze zdjęcie (cała ryba z boku, ostro, bez odblasków).")
    if (needsConfirm) Text("Wynik niepewny lub gatunek łatwy do pomylenia – porównaj cechy przed potwierdzeniem.")
    out.candidates.take(3).forEach { cand ->
        val sp = ds.speciesById(cand.speciesId) ?: return@forEach
        val pct = cand.probability?.let { " – ${(it * 100).toInt()}%" } ?: ""
        Text("${sp.namePl} (${sp.nameLatin})$pct", style = MaterialTheme.typography.titleMedium)
        SpeciesCandidateCard(ds, sp.id) { onConfirm(sp.id) }
    }
}
