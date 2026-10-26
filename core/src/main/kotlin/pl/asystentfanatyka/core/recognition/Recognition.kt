package pl.asystentfanatyka.core.recognition

/** Propozycja gatunku od silnika rozpoznawania. */
data class RecognitionCandidate(
    val speciesId: String,
    /** Prawdopodobieństwo 0..1, tylko jeśli silnik ma skalibrowane wyniki; w przeciwnym razie null. */
    val probability: Double?
)

enum class RecognitionSource { ON_DEVICE_MODEL, REMOTE_API, IDENTIFICATION_KEY, USER_SELECTION }

data class RecognitionOutcome(
    val source: RecognitionSource,
    val candidates: List<RecognitionCandidate>,
    val needsBetterPhoto: Boolean,
    val message: String?
) {
    val top: RecognitionCandidate? get() = candidates.firstOrNull()
}

/**
 * Polityka decyzji na wynikach modelu. Nawet przy wysokim wyniku rozpoznanie wymaga
 * potwierdzenia użytkownika, gdy gatunek ma „bliźniaka” trudnego do odróżnienia.
 */
object RecognitionPolicy {
    const val MIN_TOP_PROBABILITY = 0.80
    const val MIN_MARGIN = 0.25

    fun needsConfirmation(outcome: RecognitionOutcome, confusablePairs: Set<Set<String>>): Boolean {
        val top = outcome.top ?: return true
        val p = top.probability ?: return true
        if (outcome.needsBetterPhoto) return true
        if (p < MIN_TOP_PROBABILITY) return true
        val second = outcome.candidates.getOrNull(1)?.probability ?: 0.0
        if (p - second < MIN_MARGIN) return true
        return confusablePairs.any { top.speciesId in it }
    }

    /** Pary szczególnie łatwe do pomylenia – zawsze wymagają ręcznego potwierdzenia. */
    val CONFUSABLE: Set<Set<String>> = setOf(
        setOf("klen", "jaz"), setOf("klen", "jelec"), setOf("leszcz", "krap"), setOf("leszcz", "rozpior"),
        setOf("ploc", "wzdrega"), setOf("troc", "losos"), setOf("troc", "troc_jeziorowa"),
        setOf("pstrag_potokowy", "pstrag_teczowy"), setOf("sieja", "sielawa"), setOf("sandacz", "okon"),
        setOf("karp", "karas_srebrzysty")
    )
}
