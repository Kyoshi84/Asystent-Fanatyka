package pl.asystentfanatyka.measurement

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.asystentfanatyka.camera.FitTransform
import pl.asystentfanatyka.camera.PhotoStorage
import pl.asystentfanatyka.container
import pl.asystentfanatyka.core.measurement.CalibratedMeasurementInput
import pl.asystentfanatyka.core.measurement.ImageQualityReport
import pl.asystentfanatyka.core.measurement.MeasurementCalculator
import pl.asystentfanatyka.core.measurement.MeasurementOutcome
import pl.asystentfanatyka.core.measurement.Pt
import pl.asystentfanatyka.core.rules.MeasurementInput
import pl.asystentfanatyka.core.rules.MeasurementMethod
import pl.asystentfanatyka.core.rules.MeasurementReliability
import pl.asystentfanatyka.data.AppSettings
import pl.asystentfanatyka.ui.components.PL
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.WarningBox
import pl.asystentfanatyka.ui.components.formatCm
import pl.asystentfanatyka.ui.components.formatMm
import pl.asystentfanatyka.ui.session.CatchSessionViewModel
import kotlin.math.roundToInt

private val REF_COLOR = Color(0xFF1E88E5)
private val FISH_COLOR = Color(0xFFFF6D00)

private enum class Handle { REF_A, REF_B, SNOUT, MID, TAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen(
    vm: CatchSessionViewModel,
    onTakePhoto: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalContext.current.container
    val session by vm.session.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    var mode by remember { mutableIntStateOf(if (session.photoPath != null) 1 else 0) }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quality by remember { mutableStateOf<ImageQualityReport?>(null) }
    LaunchedEffect(session.photoPath) {
        val p = session.photoPath ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val b = PhotoStorage.loadBitmap(p, PhotoStorage.MAX_SIDE)
            bitmap = b
            quality = b?.let { PhotoStorage.analyzeQuality(it) }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = mode == 0, onClick = { mode = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("Odczyt z maty")
            }
            SegmentedButton(selected = mode == 1, onClick = { mode = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("Zdjęcie ze skalą")
            }
        }
        if (mode == 0) {
            MatReadingPanel(settings, bitmap, onGraduation = { g -> scope.launch { c.settings.setMatGraduation(g) } }) { out ->
                vm.setMeasurement(
                    MeasurementInput(out.lengthCm, out.uncertaintyCm, MeasurementMethod.MAT_READING, out.reliability),
                    out.warnings, null
                )
                onDone()
            }
        } else {
            val b = bitmap
            if (session.photoPath == null) {
                Text("Pomiar ze zdjęcia wymaga zdjęcia ryby leżącej przy macie lub miarce.")
                Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Zrób lub wybierz zdjęcie") }
            } else if (b == null) {
                Text("Wczytywanie zdjęcia…")
            } else {
                PhotoMeasurePanel(b, quality) { out, json ->
                    vm.setMeasurement(
                        MeasurementInput(out.lengthCm, out.uncertaintyCm, MeasurementMethod.PHOTO_CALIBRATED, out.reliability),
                        out.warnings, json
                    )
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun MatReadingPanel(
    settings: AppSettings,
    bitmap: Bitmap?,
    onGraduation: (Double) -> Unit,
    onAccept: (MeasurementOutcome) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..300.0 }
    SectionCard("Odczyt z maty lub miarki – metoda podstawowa") {
        Text("Połóż rybę na macie: pysk zamknięty przy zerze, odczytaj długość przy końcu najdłuższego promienia płetwy ogonowej.")
        bitmap?.let {
            Image(it.asImageBitmap(), "Zdjęcie ryby", Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Fit)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { s -> text = s.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(6) },
            label = { Text("Długość [cm]") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineMedium
        )
        Text("Najmniejsza działka podziałki:", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.0 to "1 cm", 0.5 to "5 mm", 0.1 to "1 mm").forEach { (g, label) ->
                FilterChip(selected = settings.matGraduationCm == g, onClick = { onGraduation(g) }, label = { Text(label) })
            }
        }
        if (value != null) {
            val out = MeasurementCalculator.matReading(value, settings.matGraduationCm)
            Text(
                "${formatCm(out.lengthCm)} (${formatMm(out.lengthCm)}) ± ${String.format(PL, "%.1f", out.uncertaintyCm)} cm",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = { onAccept(out) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Zatwierdź pomiar") }
        } else if (text.isNotEmpty()) {
            Text("Wpisz długość od 1 do 300 cm.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PhotoMeasurePanel(
    bitmap: Bitmap,
    quality: ImageQualityReport?,
    onAccept: (MeasurementOutcome, String) -> Unit
) {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val image: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var refA by remember(bitmap) { mutableStateOf(Offset(w * 0.20f, h * 0.85f)) }
    var refB by remember(bitmap) { mutableStateOf(Offset(w * 0.60f, h * 0.85f)) }
    var snout by remember(bitmap) { mutableStateOf(Offset(w * 0.15f, h * 0.50f)) }
    var tail by remember(bitmap) { mutableStateOf(Offset(w * 0.85f, h * 0.50f)) }
    var mid by remember(bitmap) { mutableStateOf<Offset?>(null) }
    var refText by remember { mutableStateOf("30") }
    var matAlongFish by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf<Handle?>(null) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    val touchRadius = with(LocalDensity.current) { 40.dp.toPx() }
    val handleR = with(LocalDensity.current) { 12.dp.toPx() }

    val refCm = refText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.5 && it < 300 }
    val outcome: Result<MeasurementOutcome>? = refCm?.let { cm ->
        runCatching {
            MeasurementCalculator.compute(
                CalibratedMeasurementInput(
                    refStart = Pt(refA.x.toDouble(), refA.y.toDouble()),
                    refEnd = Pt(refB.x.toDouble(), refB.y.toDouble()),
                    refLengthCm = cm,
                    fishPoints = listOfNotNull(snout, mid, tail).map { Pt(it.x.toDouble(), it.y.toDouble()) },
                    imageWidth = bitmap.width, imageHeight = bitmap.height,
                    referenceIsMatAlongFish = matAlongFish,
                    quality = quality
                )
            )
        }
    }

    SectionCard("1. Wzorzec skali (niebieski)") {
        Text("Przeciągnij niebieskie punkty na dwie kreski podziałki i wpisz odległość między nimi. Im dłuższy odcinek, tym dokładniej.")
        OutlinedTextField(
            value = refText,
            onValueChange = { s -> refText = s.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(6) },
            label = { Text("Długość wzorca [cm]") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = matAlongFish, onClick = { matAlongFish = true }, label = { Text("Mata wzdłuż ryby") })
            FilterChip(selected = !matAlongFish, onClick = { matAlongFish = false; refText = "8,56" }, label = { Text("Karta płatnicza 8,56 cm") })
        }
    }
    SectionCard("2. Ryba (pomarańczowy)") {
        Text("Ustaw punkty: początek zamkniętego pyska i koniec najdłuższego promienia płetwy ogonowej. Dla wygiętej ryby dodaj punkt środkowy.")
        Text(
            "Automatyczne wykrywanie ryby nie jest dostępne w tej wersji – punkty wskazujesz ręcznie.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = {
            mid = if (mid == null) Offset((snout.x + tail.x) / 2, (snout.y + tail.y) / 2) else null
        }) { Text(if (mid == null) "Dodaj punkt środkowy (ryba wygięta)" else "Usuń punkt środkowy") }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(420.dp)) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val t = FitTransform.of(cw, ch, bitmap.width, bitmap.height)
        Image(image, "Zdjęcie do pomiaru", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap, t) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val handles = buildList {
                            add(Handle.REF_A to refA); add(Handle.REF_B to refB)
                            add(Handle.SNOUT to snout); add(Handle.TAIL to tail)
                            mid?.let { add(Handle.MID to it) }
                        }
                        val nearest = handles.minByOrNull { (t.toScreen(it.second.x, it.second.y) - pos).getDistance() }
                        dragging = nearest?.takeIf { (t.toScreen(it.second.x, it.second.y) - pos).getDistance() < touchRadius }?.first
                        dragPos = pos
                    },
                    onDrag = { change, _ ->
                        val hnd = dragging ?: return@detectDragGestures
                        change.consume()
                        dragPos = change.position
                        val p = t.toImage(change.position)
                        val clamped = Offset(p.x.coerceIn(0f, w - 1), p.y.coerceIn(0f, h - 1))
                        when (hnd) {
                            Handle.REF_A -> refA = clamped
                            Handle.REF_B -> refB = clamped
                            Handle.SNOUT -> snout = clamped
                            Handle.TAIL -> tail = clamped
                            Handle.MID -> mid = clamped
                        }
                    },
                    onDragEnd = { dragging = null; dragPos = null },
                    onDragCancel = { dragging = null; dragPos = null }
                )
            }
        ) {
            fun s(o: Offset) = t.toScreen(o.x, o.y)
            drawLine(REF_COLOR, s(refA), s(refB), strokeWidth = 6f)
            val fishPts = listOfNotNull(snout, mid, tail).map { s(it) }
            fishPts.zipWithNext { a, b -> drawLine(FISH_COLOR, a, b, strokeWidth = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))) }
            listOf(refA, refB).forEach { handle(s(it), REF_COLOR, handleR) }
            listOfNotNull(snout, mid, tail).forEach { handle(s(it), FISH_COLOR, handleR) }

            // Lupa: powiększony fragment pod palcem, w rogu przeciwnym do palca
            val dp = dragPos
            val hnd = dragging
            if (dp != null && hnd != null) {
                val target = when (hnd) {
                    Handle.REF_A -> refA; Handle.REF_B -> refB; Handle.SNOUT -> snout; Handle.TAIL -> tail; Handle.MID -> mid ?: snout
                }
                val loupe = size.minDimension * 0.38f
                val srcSide = (loupe / t.scale / 3f).coerceAtLeast(8f) // powiększenie ×3 względem widoku
                val srcX = (target.x - srcSide / 2).roundToInt().coerceIn(0, (bitmap.width - srcSide.toInt()).coerceAtLeast(0))
                val srcY = (target.y - srcSide / 2).roundToInt().coerceIn(0, (bitmap.height - srcSide.toInt()).coerceAtLeast(0))
                val dst = if (dp.x < size.width / 2) Offset(size.width - loupe - 8f, 8f) else Offset(8f, 8f)
                drawImage(
                    image,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(srcSide.toInt().coerceAtMost(bitmap.width), srcSide.toInt().coerceAtMost(bitmap.height)),
                    dstOffset = IntOffset(dst.x.roundToInt(), dst.y.roundToInt()),
                    dstSize = IntSize(loupe.roundToInt(), loupe.roundToInt())
                )
                drawRect(Color.White, dst, Size(loupe, loupe), style = Stroke(4f))
                val cx = dst.x + (target.x - srcX) / srcSide * loupe
                val cy = dst.y + (target.y - srcY) / srcSide * loupe
                drawLine(Color.Red, Offset(cx - 16f, cy), Offset(cx + 16f, cy), strokeWidth = 2f)
                drawLine(Color.Red, Offset(cx, cy - 16f), Offset(cx, cy + 16f), strokeWidth = 2f)
            }
        }
    }

    SectionCard("Wynik pomiaru") {
        when {
            refCm == null -> Text("Wpisz długość wzorca.", color = MaterialTheme.colorScheme.error)
            outcome == null -> Unit
            outcome.isFailure -> Text(outcome.exceptionOrNull()?.message ?: "Błąd pomiaru", color = MaterialTheme.colorScheme.error)
            else -> {
                val out = outcome.getOrThrow()
                Text(
                    "${formatCm(roundTo1(out.lengthCm))} (${formatMm(out.lengthCm)})",
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold
                )
                Text("Niepewność: ± ${String.format(PL, "%.1f", out.uncertaintyCm)} cm (${(out.relativeUncertainty * 100).roundToInt()}%)")
                Text("Metoda: zdjęcie z kalibracją skali na wzorcu " + (if (matAlongFish) "(mata wzdłuż ryby)" else "(przedmiot referencyjny)"))
                Text(
                    "Wiarygodność: " + when (out.reliability) {
                        MeasurementReliability.RELIABLE -> "dobra"
                        MeasurementReliability.LOW -> "niska – wynik orientacyjny"
                        MeasurementReliability.UNRELIABLE -> "niewystarczająca – zmierz na macie"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                out.warnings.forEach { WarningBox(it) }
                Text(
                    "Korekta perspektywy nie jest wykonywana – dlatego zdjęcie musi być zrobione prostopadle z góry, a podziałka leżeć przy rybie.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val json = buildString {
                            append("{\"ref\":[${refA.x},${refA.y},${refB.x},${refB.y}],\"refCm\":$refCm,\"fish\":[")
                            append(listOfNotNull(snout, mid, tail).joinToString(",") { "[${it.x},${it.y}]" })
                            append("],\"image\":[${bitmap.width},${bitmap.height}]}")
                        }
                        onAccept(out, json)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Zatwierdź pomiar") }
            }
        }
    }
}

private fun DrawScope.handle(center: Offset, color: Color, r: Float) {
    drawCircle(Color.White, r * 1.3f, center)
    drawCircle(color, r, center)
    drawCircle(Color.White, r * 0.3f, center)
}

private fun roundTo1(v: Double) = (v * 10).roundToInt() / 10.0
