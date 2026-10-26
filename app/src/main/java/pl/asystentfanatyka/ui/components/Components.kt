package pl.asystentfanatyka.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.asystentfanatyka.core.model.Area
import pl.asystentfanatyka.core.model.FishSpecies
import pl.asystentfanatyka.core.model.RegulationDataset
import pl.asystentfanatyka.core.rules.AreaContext
import pl.asystentfanatyka.ui.theme.BigButtonText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val PL: Locale = Locale.forLanguageTag("pl-PL")
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", PL)
fun LocalDate.pl(): String = format(DATE_FMT)

/** Duży przycisk akcji – łatwy do trafienia jedną ręką, także w rękawiczkach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BigActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primary: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 96.dp),
        colors = if (primary) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) else CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text, style = BigButtonText)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun WarningBox(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Pasek informujący o dacie weryfikacji danych i ewentualnej nieaktualności. */
@Composable
fun DataFreshnessBanner(dataset: RegulationDataset, stalenessDays: Int, modifier: Modifier = Modifier) {
    val days = ChronoUnit.DAYS.between(dataset.metadata.verifiedOn, LocalDate.now())
    val stale = days > stalenessDays
    if (stale || dataset.metadata.demo) {
        WarningBox(
            buildString {
                if (dataset.metadata.demo) append("Baza demonstracyjna – nie stanowi podstawy prawnej. ")
                if (stale) append("Dane przepisów zweryfikowano ${dataset.metadata.verifiedOn.pl()} ($days dni temu). Mogą być nieaktualne – zaktualizuj w Ustawieniach.")
            },
            modifier
        )
    } else {
        Text(
            "Przepisy krajowe zweryfikowane: ${dataset.metadata.verifiedOn.pl()} · wersja danych ${dataset.metadata.version}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("$label: ${date.pl()}", style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Wybierz") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Anuluj") } }
        ) {
            DatePicker(state = state)
        }
    }
}

/** Wybór obszaru w rozumieniu przepisów (np. „Wisła od Włocławka do ujścia”). „Nie wiem” = sprawdź wszystkie warianty. */
@Composable
fun AreaSelector(areas: List<Area>, value: AreaContext, onChange: (AreaContext) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Niektóre przepisy zależą od rzeki. Zaznacz, jeśli łowisz w jednym z tych obszarów:",
            style = MaterialTheme.typography.bodyMedium
        )
        FilterChip(
            selected = value is AreaContext.Unknown,
            onClick = { onChange(AreaContext.Unknown) },
            label = { Text("Nie wiem – pokaż, od czego zależy wynik") }
        )
        FilterChip(
            selected = value is AreaContext.Known && value.areaCodes.isEmpty(),
            onClick = { onChange(AreaContext.Known(emptySet())) },
            label = { Text("Żaden z wymienionych (pozostałe wody)") }
        )
        areas.forEach { a ->
            val selected = value is AreaContext.Known && a.code in value.areaCodes
            FilterChip(
                selected = selected,
                onClick = {
                    val cur = (value as? AreaContext.Known)?.areaCodes ?: emptySet()
                    val next = if (selected) cur - a.code else cur + a.code + a.implies
                    onChange(AreaContext.Known(next))
                },
                label = {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(a.name, fontWeight = FontWeight.SemiBold)
                        Text(a.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
        }
    }
}

/** Wybór gatunku z wyszukiwarką. */
@Composable
fun SpeciesPickerDialog(species: List<FishSpecies>, onDismiss: () -> Unit, onPick: (FishSpecies) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, species) {
        val q = query.trim().lowercase(PL)
        if (q.isEmpty()) species else species.filter { it.namePl.lowercase(PL).contains(q) || it.nameLatin.lowercase(PL).contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
        title = { Text("Wybierz gatunek") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Szukaj (nazwa polska lub łacińska)") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it.id }) { sp ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onPick(sp) }.padding(vertical = 12.dp)
                        ) {
                            Text(sp.namePl, style = MaterialTheme.typography.titleMedium)
                            Text(sp.nameLatin, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    )
}

fun formatCm(v: Double): String = if (v == Math.floor(v)) "${v.toLong()} cm" else String.format(PL, "%.1f cm", v)
fun formatMm(v: Double): String = String.format(PL, "%.0f mm", v * 10)

/** Ogólny rysunek ryby (bez odwzorowania konkretnego gatunku) – używany zamiast zdjęć, których licencji nie zweryfikowano. */
@Composable
fun FishIllustration(modifier: Modifier = Modifier) {
    val body = MaterialTheme.colorScheme.primary
    val fin = MaterialTheme.colorScheme.primaryContainer
    val eye = MaterialTheme.colorScheme.onPrimary
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.45f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.05f, h * 0.5f)
            cubicTo(w * 0.2f, h * 0.05f, w * 0.6f, h * 0.05f, w * 0.78f, h * 0.5f)
            cubicTo(w * 0.6f, h * 0.95f, w * 0.2f, h * 0.95f, w * 0.05f, h * 0.5f)
            close()
        }
        val tail = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.74f, h * 0.5f)
            lineTo(w * 0.97f, h * 0.15f)
            lineTo(w * 0.92f, h * 0.5f)
            lineTo(w * 0.97f, h * 0.85f)
            close()
        }
        drawPath(tail, fin)
        drawPath(path, body)
        drawCircle(eye, radius = h * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.17f, h * 0.42f))
        drawLine(fin, androidx.compose.ui.geometry.Offset(cx - w * 0.05f, h * 0.5f), androidx.compose.ui.geometry.Offset(w * 0.7f, h * 0.5f), strokeWidth = h * 0.02f)
    }
}
