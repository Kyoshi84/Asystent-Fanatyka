package pl.asystentfanatyka.core.measurement

import pl.asystentfanatyka.core.rules.MeasurementReliability
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

data class Pt(val x: Double, val y: Double) {
    fun dist(o: Pt) = hypot(x - o.x, y - o.y)
}

/**
 * Dane wejściowe pomiaru z kalibracją: odcinek wzorca o znanej długości (np. 20 cm na macie)
 * oraz punkty na rybie od początku zamkniętego pyska do końca najdłuższego promienia płetwy ogonowej.
 * Punkty pośrednie pozwalają poprowadzić linię wzdłuż wygiętej ryby.
 */
data class CalibratedMeasurementInput(
    val refStart: Pt,
    val refEnd: Pt,
    val refLengthCm: Double,
    /** Punkty wzdłuż ryby: [pysk, (opcjonalne punkty pośrednie), koniec płetwy ogonowej]. */
    val fishPoints: List<Pt>,
    val imageWidth: Int,
    val imageHeight: Int,
    /** Szacowany błąd wskazania punktu w pikselach obrazu (zależy od powiększenia przy wskazywaniu). */
    val tapErrorPx: Double = 0.0035 * maxOf(imageWidth, imageHeight),
    /** Czy wzorcem jest mata/miarka ułożona wzdłuż ryby (najlepszy przypadek). */
    val referenceIsMatAlongFish: Boolean,
    val quality: ImageQualityReport? = null
)

data class MeasurementOutcome(
    val lengthCm: Double,
    val uncertaintyCm: Double,
    val relativeUncertainty: Double,
    val pxPerCm: Double,
    val reliability: MeasurementReliability,
    val warnings: List<String>
)

object MeasurementCalculator {

    fun compute(input: CalibratedMeasurementInput): MeasurementOutcome {
        require(input.refLengthCm > 0) { "Długość wzorca musi być dodatnia" }
        require(input.fishPoints.size >= 2) { "Wskaż co najmniej pysk i koniec płetwy ogonowej" }
        val warnings = mutableListOf<String>()

        val refPx = input.refStart.dist(input.refEnd)
        require(refPx >= 1.0) { "Punkty wzorca pokrywają się – wskaż oba końce odcinka" }
        val pxPerCm = refPx / input.refLengthCm

        val segments = input.fishPoints.zipWithNext { a, b -> a.dist(b) }
        val fishPx = segments.sum()
        require(fishPx >= 1.0) { "Punkty na rybie pokrywają się" }
        val lengthCm = fishPx / pxPerCm

        // Błąd wskazania: każdy koniec odcinka obarczony błędem e → błąd długości ≈ √2·e na odcinek.
        val e = input.tapErrorPx
        val relRef = sqrt(2.0) * e / refPx
        val relFish = sqrt(2.0) * e * sqrt(segments.size.toDouble()) / fishPx

        // Geometria: wzorzec krótki względem ryby, nierównoległy lub daleko od ryby → większy wpływ perspektywy.
        var geometry = if (input.referenceIsMatAlongFish) 0.01 else 0.03
        val ratio = refPx / fishPx
        if (ratio < 0.3) {
            warnings += "Wzorzec jest krótki w porównaniu z rybą (${(ratio * 100).toInt()}% długości). Użyj dłuższego odcinka maty."
            geometry += 0.02
        }
        val angle = angleDeg(input.refStart, input.refEnd, input.fishPoints.first(), input.fishPoints.last())
        if (angle > 15) {
            warnings += "Wzorzec nie jest równoległy do ryby (kąt ok. ${angle.toInt()}°). Skala może się różnić w różnych kierunkach zdjęcia."
            geometry += 0.02
        }
        val refMid = Pt((input.refStart.x + input.refEnd.x) / 2, (input.refStart.y + input.refEnd.y) / 2)
        val distToFish = distanceToPolyline(refMid, input.fishPoints)
        if (distToFish > 0.5 * fishPx) {
            warnings += "Wzorzec leży daleko od ryby. Połóż miarkę bezpośrednio przy rybie, na tej samej płaszczyźnie."
            geometry += 0.03
        }
        if (input.fishPoints.size > 2) {
            val straight = input.fishPoints.first().dist(input.fishPoints.last())
            if (fishPx / straight > 1.05) {
                warnings += "Ryba jest wygięta – długość zmierzono wzdłuż linii łamanej. Wyprostuj rybę, jeśli to możliwe."
                geometry += 0.01
            }
        }
        val maxDim = maxOf(input.imageWidth, input.imageHeight).toDouble()
        if (fishPx < 0.35 * maxDim) {
            warnings += "Ryba zajmuje małą część kadru. Zrób zdjęcie z bliższej odległości, prostopadle z góry."
            geometry += 0.01
        }
        // Punkty przy samej krawędzi zdjęcia – największe zniekształcenia obiektywu.
        val margin = 0.03 * maxDim
        val near = (input.fishPoints + listOf(input.refStart, input.refEnd)).any {
            it.x < margin || it.y < margin || it.x > input.imageWidth - margin || it.y > input.imageHeight - margin
        }
        if (near) {
            warnings += "Punkty pomiarowe leżą przy krawędzi zdjęcia, gdzie obiektyw zniekształca obraz."
            geometry += 0.01
        }

        val q = input.quality
        var qualityPenalty = false
        if (q != null) {
            warnings += q.warnings
            if (!q.acceptable) qualityPenalty = true
        }

        val rel = sqrt(relRef * relRef + relFish * relFish + geometry * geometry)
        val reliability = when {
            qualityPenalty && rel > 0.05 -> MeasurementReliability.UNRELIABLE
            rel <= 0.03 && !qualityPenalty -> MeasurementReliability.RELIABLE
            rel <= 0.08 -> MeasurementReliability.LOW
            else -> MeasurementReliability.UNRELIABLE
        }
        return MeasurementOutcome(
            lengthCm = lengthCm,
            uncertaintyCm = lengthCm * rel,
            relativeUncertainty = rel,
            pxPerCm = pxPerCm,
            reliability = reliability,
            warnings = warnings
        )
    }

    /** Odczyt z maty/miarki wpisany przez użytkownika. Niepewność = połowa najmniejszej działki podziałki. */
    fun matReading(lengthCm: Double, graduationCm: Double = 1.0): MeasurementOutcome {
        require(lengthCm > 0) { "Długość musi być dodatnia" }
        require(graduationCm > 0) { "Działka podziałki musi być dodatnia" }
        val u = graduationCm / 2
        return MeasurementOutcome(lengthCm, u, u / lengthCm, 0.0, MeasurementReliability.RELIABLE, emptyList())
    }

    private fun angleDeg(a1: Pt, a2: Pt, b1: Pt, b2: Pt): Double {
        val ax = a2.x - a1.x; val ay = a2.y - a1.y
        val bx = b2.x - b1.x; val by = b2.y - b1.y
        val na = hypot(ax, ay); val nb = hypot(bx, by)
        if (na == 0.0 || nb == 0.0) return 0.0
        val cos = abs(ax * bx + ay * by) / (na * nb)
        return Math.toDegrees(acos(cos.coerceIn(0.0, 1.0)))
    }

    private fun distanceToPolyline(p: Pt, pts: List<Pt>): Double =
        pts.zipWithNext { a, b -> distanceToSegment(p, a, b) }.minOrNull() ?: Double.MAX_VALUE

    private fun distanceToSegment(p: Pt, a: Pt, b: Pt): Double {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return p.dist(a)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        return p.dist(Pt(a.x + t * dx, a.y + t * dy))
    }
}
