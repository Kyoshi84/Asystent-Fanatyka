package pl.asystentfanatyka.recognition

import android.graphics.Bitmap
import pl.asystentfanatyka.core.recognition.RecognitionOutcome

/** Stan silnika rozpoznawania widoczny w interfejsie – aplikacja nigdy nie udaje, że model działa. */
sealed interface RecognizerStatus {
    data object Ready : RecognizerStatus
    data class NotConfigured(val reason: String) : RecognizerStatus
    data class Unavailable(val reason: String) : RecognizerStatus
}

/**
 * Wspólny interfejs dla wariantów rozpoznawania:
 *  - model lokalny (np. TensorFlow Lite / LiteRT) – praca offline, zdjęcie nie opuszcza telefonu,
 *  - zewnętrzne API – wymaga internetu i zgody na wysłanie zdjęcia,
 *  - hybryda – model lokalny, a przy niskiej pewności opcjonalnie API.
 * Wynik każdego wariantu przechodzi przez [pl.asystentfanatyka.core.recognition.RecognitionPolicy]
 * i zawsze wymaga potwierdzenia gatunku przez użytkownika, zanim trafi do kontroli przepisów.
 */
interface SpeciesRecognizer {
    val displayName: String
    val status: RecognizerStatus
    /** Czy zdjęcie opuszcza urządzenie. */
    val sendsPhotoOffDevice: Boolean
    suspend fun recognize(bitmap: Bitmap): RecognitionOutcome
}

/**
 * Wariant MVP: brak dołączonego modelu. Rzetelny model do rozróżniania polskich ryb słodkowodnych
 * (w tym par kleń/jaź, leszcz/krąp, łosoś/troć) wymaga zbioru tysięcy zweryfikowanych zdjęć na gatunek,
 * którego nie ma w tym projekcie. Zamiast niego aplikacja prowadzi użytkownika kluczem cech.
 * Instrukcja dołączenia modelu: docs/ROZPOZNAWANIE_AI.md.
 */
class NotConfiguredRecognizer : SpeciesRecognizer {
    override val displayName = "Model AI"
    override val status = RecognizerStatus.NotConfigured(
        "Ta wersja nie zawiera wytrenowanego modelu rozpoznawania. Użyj klucza cech poniżej lub wybierz gatunek z listy."
    )
    override val sendsPhotoOffDevice = false
    override suspend fun recognize(bitmap: Bitmap): RecognitionOutcome =
        throw UnsupportedOperationException("Model AI nie jest skonfigurowany")
}
