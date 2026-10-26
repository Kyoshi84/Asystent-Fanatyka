package pl.asystentfanatyka.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.asystentfanatyka.container
import pl.asystentfanatyka.core.model.RuleKind
import pl.asystentfanatyka.core.model.WaterType
import pl.asystentfanatyka.core.rules.RuleEngine
import pl.asystentfanatyka.core.time.SeasonCalculator
import pl.asystentfanatyka.data.AppSettings
import pl.asystentfanatyka.data.DatasetState
import pl.asystentfanatyka.ui.components.DataFreshnessBanner
import pl.asystentfanatyka.ui.components.DateField
import pl.asystentfanatyka.ui.components.PL
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.pl
import pl.asystentfanatyka.ui.species.WATER_TYPE_PL
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH_FMT = DateTimeFormatter.ofPattern("LLLL yyyy", PL)
private val WEEK = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")

@Composable
fun CalendarScreen(onOpenSpecies: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val state by c.regulations.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val ds = (state as? DatasetState.Ready)?.dataset ?: run {
        Text("Brak danych o przepisach.", modifier.padding(16.dp)); return
    }

    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selectedDate = LocalDate.parse(selected)
    var month by remember(selected) { mutableStateOf(YearMonth.from(selectedDate)) }
    var waterFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val engine = remember(ds) { RuleEngine(ds) }
    val area = settings.areaContext
    fun closedOn(d: LocalDate) = engine.closedOn(d, area).filter { cs ->
        waterFilter == null || ds.speciesById(cs.speciesId)?.waterTypes?.contains(WaterType.valueOf(waterFilter!!)) == true
    }
    val counts = remember(ds, month, waterFilter, area) {
        (1..month.lengthOfMonth()).associateWith { closedOn(month.atDay(it)).size }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DataFreshnessBanner(ds, settings.stalenessDays)
        DateField("Dzień połowu", selectedDate, { selected = it.toString() })

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = waterFilter == null, onClick = { waterFilter = null }, label = { Text("Wszystkie wody") })
            listOf(WaterType.RIVER, WaterType.LAKE, WaterType.STREAM, WaterType.POND, WaterType.RESERVOIR).forEach { wt ->
                FilterChip(
                    selected = waterFilter == wt.name,
                    onClick = { waterFilter = wt.name },
                    label = { Text(WATER_TYPE_PL[wt] ?: wt.name) }
                )
            }
        }

        // Nagłówek miesiąca
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Poprzedni miesiąc")
            }
            Text(
                month.format(MONTH_FMT).replaceFirstChar { it.titlecase(PL) },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Następny miesiąc")
            }
        }
        MonthGrid(month, selectedDate, counts) { selected = it.toString() }
        Text(
            "Liczba w kratce = liczba gatunków objętych okresem ochronnym (przepisy krajowe" +
                (if (area is pl.asystentfanatyka.core.rules.AreaContext.Unknown) ", wszystkie warianty obszarów" else ", wybrany obszar") + ").",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val list = remember(selected, ds, waterFilter, area) { closedOn(selectedDate) }
        SectionCard("Pod ochroną ${selectedDate.pl()}") {
            if (list.isEmpty()) Text("Żaden gatunek z bazy nie ma okresu ochronnego w tym dniu.")
            list.forEach { cs ->
                Column(Modifier.fillMaxWidth().clickable { onOpenSpecies(cs.speciesId) }.padding(vertical = 6.dp)) {
                    Text(cs.speciesName, style = MaterialTheme.typography.titleMedium)
                    cs.rules.forEach { r ->
                        val areaName = when (r.areaCode) {
                            null -> ""
                            RuleEngine.OTHER -> " (pozostałe wody)"
                            else -> " (" + (ds.areas.firstOrNull { it.code == r.areaCode }?.name ?: r.areaCode) + ")"
                        }
                        val what = if (r.kind == RuleKind.WEEKDAY_BAN) "zakaz połowu w wybrane dni tygodnia" else "okres ochronny"
                        Text(
                            "$what: ${SeasonCalculator.describe(r.seasonStart!!, r.seasonEnd!!)}$areaName",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            TextButton(onClick = { selected = LocalDate.now().toString() }) { Text("Wróć do dzisiaj") }
        }
        Text(
            "Kalendarz pokazuje przepisy krajowe. Regulamin okręgu PZW lub łowiska może je zaostrzać.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, counts: Map<Int, Int>, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val cells = offset + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            WEEK.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge) }
        }
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val dayNum = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val n = counts[dayNum] ?: 0
                            val isSel = date == selected
                            val bg = when {
                                isSel -> MaterialTheme.colorScheme.primary
                                n > 0 -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Column(
                                Modifier.fillMaxSize()
                                    .background(bg, RoundedCornerShape(8.dp))
                                    .border(
                                        if (date == today) 2.dp else 0.dp,
                                        if (date == today) MaterialTheme.colorScheme.primary else bg,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelect(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("$dayNum", color = fg, fontWeight = FontWeight.Bold)
                                if (n > 0) Text("$n", color = fg, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
