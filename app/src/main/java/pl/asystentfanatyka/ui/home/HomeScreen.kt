package pl.asystentfanatyka.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.asystentfanatyka.container
import pl.asystentfanatyka.core.rules.RuleEngine
import pl.asystentfanatyka.data.AppSettings
import pl.asystentfanatyka.data.DatasetState
import pl.asystentfanatyka.ui.components.BigActionButton
import pl.asystentfanatyka.ui.components.DataFreshnessBanner
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.WarningBox
import java.time.LocalDate

@Composable
fun HomeScreen(
    onRecognize: () -> Unit,
    onMeasure: () -> Unit,
    onCalendar: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalContext.current.container
    val state by c.regulations.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BigActionButton("Rozpoznaj rybę", Icons.Filled.CameraAlt, onRecognize, subtitle = "Zdjęcie i klucz cech")
        BigActionButton("Zmierz rybę", Icons.Filled.Straighten, onMeasure, subtitle = "Mata, miarka lub zdjęcie ze skalą")
        BigActionButton("Kalendarz ochronny", Icons.Filled.CalendarMonth, onCalendar, subtitle = "Kto jest dziś pod ochroną", primary = false)
        BigActionButton("Sprawdź przepisy łowiska", Icons.Filled.Gavel, onCheck, subtitle = "Czy mogę zatrzymać tę rybę?", primary = false)

        when (val s = state) {
            DatasetState.Loading -> Text("Wczytywanie danych o przepisach…")
            is DatasetState.Error -> WarningBox(s.message)
            is DatasetState.Ready -> {
                DataFreshnessBanner(s.dataset, settings.stalenessDays)
                val today = LocalDate.now()
                val closed = remember(s.dataset, today, settings.areaContext) {
                    RuleEngine(s.dataset).closedOn(today, settings.areaContext)
                }
                SectionCard("Dziś pod ochroną (przepisy krajowe)") {
                    if (closed.isEmpty()) Text("Żaden gatunek z bazy nie ma dziś okresu ochronnego.")
                    else closed.forEach {
                        Text(
                            "• ${it.speciesName}" + if (!it.allAreas) " – tylko na części wód" else "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Text(
                        "Regulamin Twojego łowiska może wprowadzać dodatkowe ograniczenia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
