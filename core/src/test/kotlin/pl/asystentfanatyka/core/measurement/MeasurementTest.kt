package pl.asystentfanatyka.core.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.asystentfanatyka.core.rules.MeasurementReliability

class MeasurementCalculatorTest {

    @Test fun `ryba na macie - poprawna dlugosc i wysoka wiarygodnosc`() {
        // Zdjęcie 4000×3000, mata: 40 cm = 2000 px (50 px/cm). Ryba od x=400 do x=3000 → 52 cm.
        val out = MeasurementCalculator.compute(
            CalibratedMeasurementInput(
                refStart = Pt(400.0, 1800.0), refEnd = Pt(2400.0, 1800.0), refLengthCm = 40.0,
                fishPoints = listOf(Pt(400.0, 1500.0), Pt(3000.0, 1500.0)),
                imageWidth = 4000, imageHeight = 3000, tapErrorPx = 6.0, referenceIsMatAlongFish = true
            )
        )
        assertEquals(52.0, out.lengthCm, 1e-9)
        assertEquals(50.0, out.pxPerCm, 1e-9)
        assertEquals(MeasurementReliability.RELIABLE, out.reliability)
        assertTrue(out.uncertaintyCm in 0.3..1.5)
    }

    @Test fun `krotki wzorzec daleko od ryby i nierownolegly - niska wiarygodnosc`() {
        val out = MeasurementCalculator.compute(
            CalibratedMeasurementInput(
                refStart = Pt(3500.0, 200.0), refEnd = Pt(3500.0, 500.0), refLengthCm = 8.56, // karta płatnicza
                fishPoints = listOf(Pt(500.0, 2000.0), Pt(3000.0, 2100.0)),
                imageWidth = 4000, imageHeight = 3000, referenceIsMatAlongFish = false
            )
        )
        assertTrue(out.reliability != MeasurementReliability.RELIABLE)
        assertTrue(out.warnings.any { it.contains("krótki") })
        assertTrue(out.warnings.any { it.contains("równoległy") })
        assertTrue(out.warnings.any { it.contains("daleko") })
    }

    @Test fun `wygieta ryba mierzona linia lamana`() {
        val out = MeasurementCalculator.compute(
            CalibratedMeasurementInput(
                refStart = Pt(500.0, 2500.0), refEnd = Pt(3500.0, 2500.0), refLengthCm = 60.0,
                fishPoints = listOf(Pt(500.0, 1500.0), Pt(2000.0, 1000.0), Pt(3500.0, 1500.0)),
                imageWidth = 4000, imageHeight = 3000, tapErrorPx = 6.0, referenceIsMatAlongFish = true
            )
        )
        val expectedPx = 2 * Math.hypot(1500.0, 500.0)
        assertEquals(expectedPx / 50.0, out.lengthCm, 1e-9)
        assertTrue(out.warnings.any { it.contains("wygięta") })
    }

    @Test fun `zla jakosc zdjecia obniza wiarygodnosc`() {
        val badQuality = ImageQualityReport(30.0, 10.0, 0.0, 0.5, acceptable = false, warnings = listOf("Zdjęcie może być nieostre."))
        val out = MeasurementCalculator.compute(
            CalibratedMeasurementInput(
                refStart = Pt(400.0, 1800.0), refEnd = Pt(2400.0, 1800.0), refLengthCm = 40.0,
                fishPoints = listOf(Pt(400.0, 1500.0), Pt(3000.0, 1500.0)),
                imageWidth = 4000, imageHeight = 3000, tapErrorPx = 6.0, referenceIsMatAlongFish = true,
                quality = badQuality
            )
        )
        assertTrue(out.reliability != MeasurementReliability.RELIABLE)
        assertTrue(out.warnings.contains("Zdjęcie może być nieostre."))
    }

    @Test fun `odczyt z maty`() {
        val out = MeasurementCalculator.matReading(46.0, graduationCm = 1.0)
        assertEquals(0.5, out.uncertaintyCm, 1e-9)
        assertEquals(MeasurementReliability.RELIABLE, out.reliability)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pokrywajace sie punkty wzorca`() {
        MeasurementCalculator.compute(
            CalibratedMeasurementInput(Pt(1.0, 1.0), Pt(1.0, 1.0), 10.0, listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), 100, 100, referenceIsMatAlongFish = true)
        )
    }
}

class ImageQualityTest {
    @Test fun `jednolity obraz jest nieostry`() {
        val r = ImageQuality.analyze(IntArray(100 * 100) { 128 }, 100, 100)
        assertTrue(!r.acceptable)
        assertTrue(r.warnings.any { it.contains("nieostre") })
    }

    @Test fun `szachownica jest ostra`() {
        val w = 100
        val img = IntArray(w * w) { i -> if (((i % w) / 4 + (i / w) / 4) % 2 == 0) 60 else 190 }
        val r = ImageQuality.analyze(img, w, w)
        assertTrue(r.sharpness > ImageQuality.BLUR_THRESHOLD)
        assertTrue(r.acceptable)
    }

    @Test fun `ciemny obraz`() {
        val r = ImageQuality.analyze(IntArray(50 * 50) { if (it % 2 == 0) 0 else 40 }, 50, 50)
        assertTrue(r.warnings.any { it.contains("ciemne") })
    }
}
