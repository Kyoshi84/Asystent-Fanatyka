package pl.asystentfanatyka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.asystentfanatyka.camera.CameraScreen
import pl.asystentfanatyka.camera.CropScreen
import pl.asystentfanatyka.camera.PhotoPurpose
import pl.asystentfanatyka.camera.PhotoSourceScreen
import pl.asystentfanatyka.core.rules.SpeciesCertainty
import pl.asystentfanatyka.measurement.MeasureScreen
import pl.asystentfanatyka.recognition.RecognitionScreen
import pl.asystentfanatyka.regulations.CheckScreen
import pl.asystentfanatyka.regulations.ResultScreen
import pl.asystentfanatyka.ui.calendar.CalendarScreen
import pl.asystentfanatyka.ui.home.HomeScreen
import pl.asystentfanatyka.ui.saved.SavedDetailScreen
import pl.asystentfanatyka.ui.saved.SavedScreen
import pl.asystentfanatyka.ui.session.CatchSessionViewModel
import pl.asystentfanatyka.ui.settings.SettingsScreen
import pl.asystentfanatyka.ui.species.SpeciesCardScreen
import pl.asystentfanatyka.ui.species.SpeciesListScreen
import pl.asystentfanatyka.ui.theme.AsystentFanatykaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AsystentFanatykaTheme { AppRoot() } }
    }
}

private object R0 {
    const val START = "start"
    const val SPECIES = "species"
    const val CALENDAR = "calendar"
    const val SAVED = "saved"
    const val SPECIES_CARD = "species/{id}"
    const val PHOTO = "photo/{purpose}"
    const val CAMERA = "camera/{purpose}"
    const val CROP = "crop/{purpose}"
    const val RECOGNIZE = "recognize"
    const val MEASURE = "measure"
    const val CHECK = "check"
    const val RESULT = "result"
    const val SETTINGS = "settings"
    const val SAVED_DETAIL = "saved/{id}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(R0.START, "Start", Icons.Filled.Home),
    Tab(R0.SPECIES, "Gatunki", Icons.AutoMirrored.Filled.List),
    Tab(R0.CALENDAR, "Kalendarz", Icons.Filled.CalendarMonth),
    Tab(R0.SAVED, "Zapisane", Icons.Filled.Bookmarks)
)

private fun titleFor(route: String?): String = when (route) {
    R0.START, null -> "Asystent Fanatyka"
    R0.SPECIES -> "Gatunki"
    R0.CALENDAR -> "Kalendarz ochronny"
    R0.SAVED -> "Zapisane połowy"
    R0.SPECIES_CARD -> "Karta gatunku"
    R0.PHOTO -> "Zdjęcie ryby"
    R0.CAMERA -> "Aparat"
    R0.CROP -> "Przytnij zdjęcie"
    R0.RECOGNIZE -> "Rozpoznawanie"
    R0.MEASURE -> "Pomiar długości"
    R0.CHECK -> "Czy mogę zatrzymać?"
    R0.RESULT -> "Wynik kontroli"
    R0.SETTINGS -> "Ustawienia i źródła"
    R0.SAVED_DETAIL -> "Zapisany połów"
    else -> "Asystent Fanatyka"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val vm: CatchSessionViewModel = viewModel()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = TABS.any { it.route == route }

    Scaffold(
        topBar = {
            if (route != R0.CAMERA) TopAppBar(
                title = { Text(titleFor(route)) },
                navigationIcon = {
                    if (!isTab && route != null) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                    }
                },
                actions = {
                    if (route == R0.START) IconButton(onClick = { nav.navigate(R0.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, "Ustawienia")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            if (route != R0.CAMERA) NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { nav.navigateTab(tab.route) },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = R0.START, modifier = Modifier.padding(padding)) {
            composable(R0.START) {
                HomeScreen(
                    onRecognize = { vm.reset(); nav.navigate("photo/${PhotoPurpose.RECOGNIZE.name}") },
                    onMeasure = { vm.reset(); nav.navigate("photo/${PhotoPurpose.MEASURE.name}") },
                    onCalendar = { nav.navigateTab(R0.CALENDAR) },
                    onCheck = { nav.navigate(R0.CHECK) }
                )
            }
            composable(R0.SPECIES) { SpeciesListScreen(onOpen = { nav.navigate("species/$it") }) }
            composable(R0.CALENDAR) { CalendarScreen(onOpenSpecies = { nav.navigate("species/$it") }) }
            composable(R0.SAVED) { SavedScreen(onOpen = { nav.navigate("saved/$it") }) }
            composable(R0.SETTINGS) { SettingsScreen() }

            composable(R0.SPECIES_CARD, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                SpeciesCardScreen(
                    speciesId = e.arguments?.getString("id").orEmpty(),
                    onCheck = { id -> vm.reset(); vm.setSpecies(id, SpeciesCertainty.CONFIRMED); nav.navigate(R0.CHECK) },
                    onOpenSpecies = { nav.navigate("species/$it") }
                )
            }
            composable(R0.SAVED_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                SavedDetailScreen(e.arguments?.getLong("id") ?: 0L, onDeleted = { nav.popBackStack() })
            }

            composable(R0.PHOTO, arguments = listOf(navArgument("purpose") { type = NavType.StringType })) { e ->
                val purpose = purposeOf(e.arguments?.getString("purpose"))
                PhotoSourceScreen(
                    purpose = purpose,
                    onCamera = { nav.navigate("camera/${purpose.name}") },
                    onPhotoReady = { path -> vm.setPhoto(path); nav.navigate("crop/${purpose.name}") },
                    onSkipPhoto = {
                        if (purpose == PhotoPurpose.MEASURE) nav.navigate(R0.MEASURE) else nav.navigate(R0.RECOGNIZE)
                    }
                )
            }
            composable(R0.CAMERA, arguments = listOf(navArgument("purpose") { type = NavType.StringType })) { e ->
                val purpose = purposeOf(e.arguments?.getString("purpose"))
                CameraScreen(
                    onCaptured = { path ->
                        vm.setPhoto(path)
                        nav.navigate("crop/${purpose.name}") { popUpTo(R0.CAMERA) { inclusive = true } }
                    },
                    onCancel = { nav.popBackStack() }
                )
            }
            composable(R0.CROP, arguments = listOf(navArgument("purpose") { type = NavType.StringType })) { e ->
                val purpose = purposeOf(e.arguments?.getString("purpose"))
                val session by vm.session.collectAsStateWithLifecycle()
                val path = session.photoPath
                if (path == null) {
                    Text("Brak zdjęcia.")
                } else CropScreen(path, onDone = { out ->
                    vm.setPhoto(out)
                    val next = if (purpose == PhotoPurpose.MEASURE) R0.MEASURE else R0.RECOGNIZE
                    nav.navigate(next) { popUpTo(R0.PHOTO) { inclusive = true } }
                })
            }
            composable(R0.RECOGNIZE) {
                RecognitionScreen(
                    vm = vm,
                    onConfirmed = { id ->
                        val needsLength = vm.lengthRulesExist(id)
                        nav.navigate(if (needsLength) R0.MEASURE else R0.CHECK)
                    },
                    onRetakePhoto = { nav.navigate("photo/${PhotoPurpose.RECOGNIZE.name}") }
                )
            }
            composable(R0.MEASURE) {
                MeasureScreen(
                    vm = vm,
                    onTakePhoto = { nav.navigate("photo/${PhotoPurpose.MEASURE.name}") },
                    onDone = { nav.navigate(R0.CHECK) { popUpTo(R0.CHECK) { inclusive = true } } }
                )
            }
            composable(R0.CHECK) {
                CheckScreen(
                    vm = vm,
                    onMeasure = { nav.navigate(R0.MEASURE) },
                    onRecognize = { nav.navigate("photo/${PhotoPurpose.RECOGNIZE.name}") },
                    onResult = { nav.navigate(R0.RESULT) }
                )
            }
            composable(R0.RESULT) {
                ResultScreen(
                    vm = vm,
                    onNew = {
                        vm.reset()
                        nav.navigate(R0.START) { popUpTo(nav.graph.findStartDestination().id) { inclusive = true } }
                    },
                    onSaved = { }
                )
            }
        }
    }
}

private fun purposeOf(s: String?) = runCatching { PhotoPurpose.valueOf(s ?: "") }.getOrDefault(PhotoPurpose.RECOGNIZE)

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
