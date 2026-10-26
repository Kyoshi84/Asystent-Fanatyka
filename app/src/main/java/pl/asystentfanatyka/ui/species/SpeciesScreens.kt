package pl.asystentfanatyka.ui.species

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.asystentfanatyka.container
import pl.asystentfanatyka.core.model.FishSpecies
import pl.asystentfanatyka.core.model.ProtectionRule
import pl.asystentfanatyka.core.model.RegulationDataset
import pl.asystentfanatyka.core.model.RuleKind
import pl.asystentfanatyka.core.model.RuleScope
import pl.asystentfanatyka.core.model.VerificationStatus
import pl.asystentfanatyka.core.model.WaterType
import pl.asystentfanatyka.core.rules.RuleEngine
import pl.asystentfanatyka.core.time.SeasonCalculator
import pl.asystentfanatyka.data.DatasetState
import pl.asystentfanatyka.ui.components.FishIllustration
import pl.asystentfanatyka.ui.components.PL
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.WarningBox
import pl.asystentfanatyka.ui.components.pl
import java.time.LocalDate

val WATER_TYPE_PL = mapOf(
    WaterType.RIVER to "rzeka", WaterType.LAKE to "jezioro", WaterType.STREAM to "potok",
    WaterType.POND to "staw", WaterType.RESERVOIR to "zbiornik zaporowy", WaterType.ESTUARY to "ujście / zalew"
)

@Composable
fun SpeciesListScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val state by c.regulations.state.collectAsStateWithLifecycle()
    val ds = (state as? DatasetState.Ready)?.dataset
    var query by rememberSaveable { mutableStateOf("") }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            label = { Text("Szukaj gatunku") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        if (ds == null) {
            Text("Brak danych o gatunkach.")
            return@Column
        }
        val closedToday = remember(ds) { RuleEngine(ds).closedOn(LocalDate.now()).map { it.speciesId }.toSet() }
        val list = remember(ds, query) {
            val q = query.trim().lowercase(PL)
            ds.species.filter { q.isEmpty() || it.namePl.lowercase(PL).contains(q) || it.nameLatin.lowercase(PL).contains(q) }
                .sortedBy { it.namePl.lowercase(PL) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { sp ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(sp.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FishIllustration(Modifier.size(56.dp, 28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sp.namePl, style = MaterialTheme.typography.titleMedium)
                        Text(sp.nameLatin, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                    }
                    when {
                        sp.invasive -> AssistChip(onClick = { onOpen(sp.id) }, label = { Text("inwazyjny") })
                        sp.id in closedToday -> AssistChip(onClick = { onOpen(sp.id) }, label = { Text("ochrona dziś") })
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SpeciesCardScreen(speciesId: String, onCheck: (String) -> Unit, onOpenSpecies: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val state by ctx.container.regulations.state.collectAsStateWithLifecycle()
    val ds = (state as? DatasetState.Ready)?.dataset ?: return
    val sp = ds.speciesById(speciesId) ?: run { Text("Nie znaleziono gatunku."); return }
    val rules = ds.rulesFor(sp.id).filter { it.scope == RuleScope.NATIONAL }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FishIllustration(Modifier.fillMaxWidth().height(110.dp))
        Text(sp.namePl, style = MaterialTheme.typography.headlineMedium)
        Text(sp.nameLatin, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
        Text(
            "Zdjęcie poglądowe: brak zweryfikowanych zdjęć z licencją w tej wersji – pokazano rysunek ogólny.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (sp.invasive) WarningBox("Gatunek inwazyjny. Złowionego osobnika nie wolno wypuścić do łowiska ani innych wód – należy go niezwłocznie uśmiercić.")

        SectionCard("Przepisy krajowe") { RulesSummary(ds, sp, rules) }

        SectionCard("Cechy rozpoznawcze") {
            sp.features.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            Text(sp.descriptionSource, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sp.similarSpeciesIds.isNotEmpty()) SectionCard("Podobne gatunki – porównaj") {
            sp.similarSpeciesIds.mapNotNull { ds.speciesById(it) }.forEach { other ->
                TextButton(onClick = { onOpenSpecies(other.id) }) { Text("${other.namePl} (${other.nameLatin})") }
            }
        }
        SectionCard("Środowisko") {
            Text(sp.habitat, style = MaterialTheme.typography.bodyLarge)
            if (sp.waterTypes.isNotEmpty()) Text("Typy wód: " + sp.waterTypes.joinToString(", ") { WATER_TYPE_PL[it] ?: it.name })
        }
        Button(onClick = { onCheck(sp.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Gavel, null)
            Spacer(Modifier.width(8.dp))
            Text("Sprawdź złowioną rybę tego gatunku")
        }
        SourcesFooter(ds, rules.map { it.sourceId }.distinct()) { url ->
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}

@Composable
fun RulesSummary(ds: RegulationDataset, sp: FishSpecies, rules: List<ProtectionRule>) {
    if (rules.isEmpty() || rules.all { it.kind == RuleKind.NO_RELEASE_INVASIVE }) {
        Text("Brak krajowego wymiaru i okresu ochronnego. Regulamin gospodarza wody może je wprowadzać.", style = MaterialTheme.typography.bodyLarge)
    }
    fun area(r: ProtectionRule) = when (r.areaCode) {
        null -> ""
        RuleEngine.OTHER -> " – pozostałe wody"
        else -> " – " + (ds.areas.firstOrNull { it.code == r.areaCode }?.description ?: r.areaCode)
    }
    rules.sortedBy { it.kind.ordinal }.forEach { r ->
        val text = when (r.kind) {
            RuleKind.MIN_LENGTH -> "Wymiar ochronny: zatrzymać można rybę dłuższą niż ${fmt(r.lengthCm!!)} cm${area(r)}"
            RuleKind.MAX_LENGTH -> "Górny wymiar: do ${fmt(r.lengthCm!!)} cm${area(r)}"
            RuleKind.CLOSED_SEASON -> "Okres ochronny: ${SeasonCalculator.describe(r.seasonStart!!, r.seasonEnd!!)}${area(r)}" +
                if (r.holidayShortening) "\n(skraca się o pierwszy/ostatni dzień, jeśli przypada w niedzielę lub święto)" else ""
            RuleKind.WEEKDAY_BAN -> "Zakaz połowu w wybrane dni: ${SeasonCalculator.describe(r.seasonStart!!, r.seasonEnd!!)}, " +
                r.bannedDays.sorted().joinToString(", ") { RuleEngine.dayName(LocalDate.of(2024, 1, 1).with(java.time.temporal.TemporalAdjusters.nextOrSame(it))) } + area(r)
            RuleKind.DAILY_LIMIT_COUNT -> "Limit dobowy: ${r.dailyLimit} szt.${area(r)}"
            RuleKind.NO_RELEASE_INVASIVE -> "Zakaz wypuszczania – gatunek nierodzimy"
            RuleKind.NO_KEEP -> "Zakaz zatrzymywania${area(r)}"
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (r.verification == VerificationStatus.VERIFIED) Icons.Filled.Verified else Icons.Filled.ReportProblem,
                contentDescription = if (r.verification == VerificationStatus.VERIFIED) "zweryfikowane" else "niezweryfikowane",
                tint = if (r.verification == VerificationStatus.VERIFIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${ds.sourceById(r.sourceId)?.reference ?: r.sourceId}, ${r.sourceLocator} · obowiązuje od ${r.validFrom.pl()}" +
                        (r.validTo?.let { " do ${it.pl()}" } ?: "") +
                        (r.verifiedOn?.let { " · zweryfikowano ${it.pl()}" } ?: " · NIEZWERYFIKOWANE"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                r.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun SourcesFooter(ds: RegulationDataset, sourceIds: List<String>, onOpenUrl: (String) -> Unit) {
    if (sourceIds.isEmpty()) return
    SectionCard("Źródła") {
        sourceIds.mapNotNull { ds.sourceById(it) }.forEach { s ->
            Text(s.title, style = MaterialTheme.typography.bodyMedium)
            Text(s.reference + (s.verifiedOn?.let { " · zweryfikowano ${it.pl()}" } ?: ""), style = MaterialTheme.typography.bodySmall)
            s.url?.let { url -> TextButton(onClick = { onOpenUrl(url) }) { Text("Otwórz źródło") } }
        }
    }
}

private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else String.format(PL, "%.1f", v)
