package pl.asystentfanatyka.core.measurement

data class ImageQualityReport(
    val meanBrightness: Double,
    val sharpness: Double,
    val overexposedFraction: Double,
    val underexposedFraction: Double,
    val acceptable: Boolean,
    val warnings: List<String>
)

/**
 * Prosta, heurystyczna ocena jakości zdjęcia na obrazie w skali szarości (0–255),
 * najlepiej przeskalowanym do ok. 512 px dłuższego boku.
 * Ostrość = wariancja laplasjanu. Progi dobrano zachowawczo; to wskazówka, nie gwarancja.
 */
object ImageQuality {

    const val BLUR_THRESHOLD = 60.0

    fun analyze(gray: IntArray, width: Int, height: Int): ImageQualityReport {
        require(gray.size == width * height) { "Rozmiar tablicy nie zgadza się z wymiarami" }
        require(width >= 3 && height >= 3) { "Obraz zbyt mały" }
        var sum = 0.0
        var over = 0
        var under = 0
        for (v in gray) {
            sum += v
            if (v >= 250) over++
            if (v <= 5) under++
        }
        val n = gray.size.toDouble()
        val mean = sum / n

        var lapSum = 0.0
        var lapSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val lap = (gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width] - 4 * gray[i]).toDouble()
                lapSum += lap
                lapSq += lap * lap
                count++
            }
        }
        val lapMean = lapSum / count
        val sharpness = lapSq / count - lapMean * lapMean

        val warnings = mutableListOf<String>()
        if (sharpness < BLUR_THRESHOLD) warnings += "Zdjęcie może być nieostre. Przytrzymaj telefon nieruchomo i zrób zdjęcie ponownie."
        if (mean < 50) warnings += "Zdjęcie jest zbyt ciemne."
        if (mean > 215) warnings += "Zdjęcie jest prześwietlone."
        if (over / n > 0.15) warnings += "Duża część zdjęcia jest prześwietlona (odblaski). Zmień kąt lub zacień rybę."
        if (under / n > 0.25) warnings += "Duża część zdjęcia jest bardzo ciemna."
        val acceptable = sharpness >= BLUR_THRESHOLD && mean in 50.0..215.0 && over / n <= 0.15
        return ImageQualityReport(mean, sharpness, over / n, under / n, acceptable, warnings)
    }
}
