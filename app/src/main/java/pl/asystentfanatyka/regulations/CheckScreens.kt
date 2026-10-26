package pl.asystentfanatyka.regulations

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.asystentfanatyka.container
import pl.asystentfanatyka.core.rules.CheckResult
import pl.asystentfanatyka.core.rules.FindingStatus
import pl.asystentfanatyka.core.rules.SpeciesCertainty
import pl.asystentfanatyka.core.rules.Verdict
import pl.asystentfanatyka.data.AppSettings
import pl.asystentfanatyka.data.DatasetState
import pl.asystentfanatyka.ui.components.AreaSelector
import pl.asystentfanatyka.ui.components.DataFreshnessBanner
import pl.asystentfanatyka.ui.components.DateField
import pl.asystentfanatyka.ui.components.PL
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.SpeciesPickerDialog
import pl.asystentfanatyka.ui.components.WarningBox
import pl.asystentfanatyka.ui.components.formatCm
import pl.asystentfanatyka.ui.components.formatMm
import pl.asystentfanatyka.ui.components.pl
import pl.asystentfanatyka.ui.session.CatchSessionViewModel
import pl.asystentfanatyka.ui.theme.verdictColors

/** Formularz kontroli: gatunek, długość, data, obszar, limit i regulamin łowiska. */
@Composable
fun CheckScreen(
    vm: CatchSessionViewModel,
    onMeasure: () -> Unit,
    onRecognize: () -> Unit,
    onResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalContext.current.container
    val session by vm.session.collectAsStateWithLifecycle()
    val state by c.regulations.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val ds = (state as? DatasetState.Ready)?.dataset ?: run {
        Text("Brak danych o przepisach.", Modifier.padding(16.dp)); return
    }
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var keptText by remember(session.keptToday) { mutableStateOf(session.keptToday?.toString() ?: "") }
    LaunchedEffect(session.speciesId, session.catchDate) { vm.suggestKeptToday() }

    val species = session.speciesId?.let { ds.speciesById(it) }
    val area = session.areaOverride ?: settings.areaContext
    val hasDailyLimit = species != null && ds.rulesFor(species.id).any { it.kind == pl.asystentfanatyka.core.model.RuleKind.DAILY_LIMIT_COUNT }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DataFreshnessBanner(ds, settings.stalenessDays)

        SectionCard("1. Gatunek") {
            if (species == null) Text("Nie wybrano gatunku.")
            else {
                Text(species.namePl, style = MaterialTheme.typography.titleLarge)
                Text(species.nameLatin, fontStyle = FontStyle.Italic)
                if (session.speciesCertainty != SpeciesCertainty.CONFIRMED) WarningBox("Gatunek niepotwierdzony – potwierdź go, porównując cechy.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Wybierz z listy") }
                OutlinedButton(onClick = onRecognize, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Rozpoznaj") }
            }
        }

        SectionCard("2. Długość") {
            val m = session.measurement
            if (m == null) Text("Brak pomiaru.")
            else {
                Text("${formatCm(m.lengthCm)} (${formatMm(m.lengthCm)}) ± ${String.format(PL, "%.1f", m.uncertaintyCm)} cm", style = MaterialTheme.typography.titleLarge)
                Text("Metoda: ${m.method.label}")
                session.measurementWarnings.take(2).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedButton(onClick = onMeasure, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (m == null) "Zmierz rybę" else "Zmierz ponownie")
            }
        }

        SectionCard("3. Data połowu") {
            DateField("Data", session.catchDate, { vm.setDate(it) })
        }

        SectionCard("4. Obszar w rozumieniu przepisów") {
            AreaSelector(ds.areas, area) { vm.setArea(it) }
            TextButton(onClick = {
                scope.launch {
                    when (val a = area) {
                        pl.asystentfanatyka.core.rules.AreaContext.Unknown -> c.settings.setArea(false, emptySet())
                        is pl.asystentfanatyka.core.rules.AreaContext.Known -> c.settings.setArea(true, a.areaCodes)
                    }
                }
            }) { Text("Zapamiętaj jako domyślny") }
        }

        if (hasDailyLimit) SectionCard("5. Limit dobowy") {
            OutlinedTextField(
                value = keptText,
                onValueChange = { s ->
                    keptText = s.filter { it.isDigit() }.take(3)
                    vm.setKeptToday(keptText.toIntOrNull())
                },
                label = { Text("Ile sztuk tego gatunku już dziś zatrzymałeś?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard("Regulamin łowiska") {
            Text(
                "Ta wersja aplikacji nie zawiera bazy łowisk ani regulaminów okręgów PZW. Limity dobowe, lokalne wymiary, " +
                    "okresy i zakazy sprawdź w regulaminie swojego łowiska (np. zezwolenie, strona okręgu PZW, tablica nad wodą).",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = session.localRulesConfirmed, onCheckedChange = { vm.setLocalConfirmed(it) })
                Text("Sprawdziłem regulamin mojego łowiska – nie wprowadza dla tego gatunku dodatkowych ograniczeń, których nie podałem wyżej.")
            }
        }

        error?.let { WarningBox(it) }
        Button(
            enabled = species != null,
            onClick = {
                scope.launch {
                    if (vm.runCheck()) onResult() else error = "Nie udało się przeprowadzić kontroli – sprawdź, czy wybrano gatunek i czy dane są wczytane."
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        ) { Text("Sprawdź przepisy", style = MaterialTheme.typography.titleMedium) }
    }

    if (showPicker) SpeciesPickerDialog(ds.species, { showPicker = false }) { sp ->
        showPicker = false
        vm.setSpecies(sp.id, SpeciesCertainty.CONFIRMED)
    }
}

/** Wynik kontroli – trzy stany A/B/C, z pełnym uzasadnieniem. */
@Composable
fun ResultScreen(vm: CatchSessionViewModel, onNew: () -> Unit, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val session by vm.session.collectAsStateWithLifecycle()
    val state by ctx.container.regulations.state.collectAsStateWithLifecycle()
    val ds = (state as? DatasetState.Ready)?.dataset
    val r = session.result ?: run { Text("Brak wyniku kontroli.", Modifier.padding(16.dp)); return }
    val scope = rememberCoroutineScope()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VerdictCard(r)
        if (r.mustNotRelease) WarningBox("Gatunek inwazyjny: nie wolno go wypuścić – należy go niezwłocznie uśmiercić.")

        ResultDetails(r)

        SectionCard("Sprawdzone warunki") {
            if (r.checked.isEmpty()) Text("Brak warunków, które udało się w pełni sprawdzić.")
            r.checked.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
        if (r.notVerified.isNotEmpty()) SectionCard("Czego nie udało się zweryfikować") {
            r.notVerified.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
        }
        SectionCard("Szczegóły reguł") {
            r.findings.forEach { f ->
                val mark = when (f.status) {
                    FindingStatus.PASS -> "✓"; FindingStatus.FAIL -> "✗"; FindingStatus.UNKNOWN -> "?"; FindingStatus.INFO -> "i"
                }
                Text("$mark ${f.title}", fontWeight = FontWeight.SemiBold)
                Text(f.detail, style = MaterialTheme.typography.bodyMedium)
                if (f.sourceLocator != null) Text(
                    (ds?.sourceById(f.sourceId ?: "")?.reference ?: f.sourceId ?: "") + ", " + f.sourceLocator + if (!f.verified) " · NIEZWERYFIKOWANE" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (ds != null) SectionCard("Dane i źródła") {
            Text("Dane zweryfikowano: ${r.dataVerifiedOn.pl()} (wersja ${ds.metadata.version})")
            r.sourceIds.mapNotNull { ds.sourceById(it) }.forEach { s ->
                Text("${s.title} (${s.reference})", style = MaterialTheme.typography.bodyMedium)
                s.url?.let { url -> TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Otwórz akt prawny") } }
            }
        }
        Text(
            "Aplikacja jest pomocą, nie wykładnią prawa. Odpowiedzialność za przestrzeganie przepisów ponosi wędkarz.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Nowa kontrola") }
            Button(
                enabled = session.savedId == null,
                onClick = { scope.launch { if (vm.save(null) != null) onSaved() } },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
            ) { Text(if (session.savedId == null) "Zapisz" else "Zapisano") }
        }
    }
}

@Composable
fun VerdictCard(r: CheckResult) {
    val colors = verdictColors(r.verdict)
    val (icon, headline, sub) = when (r.verdict) {
        Verdict.NOT_ALLOWED -> Triple(Icons.Filled.Block, "NIE ZATRZYMUJ", "Nie spełnia wymagań – wypuść rybę")
        Verdict.MEETS_CHECKED -> Triple(Icons.Filled.CheckCircle, "SPEŁNIA SPRAWDZONE WYMAGANIA", "Dotyczy wyłącznie warunków wymienionych poniżej")
        Verdict.CANNOT_CONFIRM -> Triple(Icons.AutoMirrored.Filled.HelpOutline, "NIE MOŻNA POTWIERDZIĆ", "Brakuje danych – w razie wątpliwości wypuść rybę")
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Wynik ${r.verdict.code}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(headline, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(sub, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(8.dp))
            Text(r.justification, fontSize = 16.sp)
        }
    }
}

@Composable
fun ResultDetails(r: CheckResult) {
    SectionCard(null) {
        Row { Text("Gatunek: ", fontWeight = FontWeight.SemiBold); Text(r.speciesName) }
        Row {
            Text("Zmierzona długość: ", fontWeight = FontWeight.SemiBold)
            Text(r.measurement?.let { "${formatCm(it.lengthCm)} ± ${String.format(PL, "%.1f", it.uncertaintyCm)} cm (${it.method.label})" } ?: "brak")
        }
        Row { Text("Wymagany wymiar: ", fontWeight = FontWeight.SemiBold); Text(r.requiredMinLengthText) }
        r.maxLengthText?.let { Row { Text("Górny wymiar: ", fontWeight = FontWeight.SemiBold); Text(it) } }
        Row { Text("Okres ochronny: ", fontWeight = FontWeight.SemiBold); Text(r.closedSeasonText) }
        Row { Text("Data połowu: ", fontWeight = FontWeight.SemiBold); Text(r.catchDate.pl()) }
        Row {
            Text("Ograniczenia lokalne: ", fontWeight = FontWeight.SemiBold)
            Text(if (r.checked.any { it.contains("Regulamin łowiska") }) "sprawdzone przez wędkarza" else "niesprawdzone")
        }
    }
}
