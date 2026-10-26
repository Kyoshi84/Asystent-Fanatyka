package pl.asystentfanatyka.core.data

import pl.asystentfanatyka.core.model.DatasetMetadata

/** Decyzja, czy przyjąć nowy, poprawnie zwalidowany zbiór danych w miejsce aktywnego. */
sealed interface ImportDecision {
    data object Accept : ImportDecision
    data class Skip(val reason: String) : ImportDecision
    data class Reject(val reason: String) : ImportDecision
}

object ImportPolicy {

    /**
     * @param requireNewer true dla aktualizacji automatycznych (sieć, dane dołączone do aplikacji):
     *        przyjmujemy tylko wyższy numer wersji. Ręczny import pliku może zastąpić dane tą samą wersją.
     * @param allowDemo czy wolno zastąpić prawdziwe dane zbiorem demonstracyjnym (tylko na wyraźne życzenie).
     */
    fun decide(active: DatasetMetadata?, incoming: DatasetMetadata, requireNewer: Boolean, allowDemo: Boolean): ImportDecision {
        if (active == null) return ImportDecision.Accept
        if (incoming.datasetId != active.datasetId && requireNewer)
            return ImportDecision.Reject("Plik dotyczy innego zbioru danych (${incoming.datasetId}) niż aktywny (${active.datasetId}).")
        if (incoming.demo && !active.demo && !allowDemo)
            return ImportDecision.Reject("Plik zawiera dane demonstracyjne – nie zastąpią one zweryfikowanej bazy.")
        if (requireNewer && incoming.version <= active.version)
            return ImportDecision.Skip("Masz już aktualną wersję danych (${active.version}).")
        if (incoming.verifiedOn.isBefore(active.verifiedOn) && requireNewer)
            return ImportDecision.Reject("Nowy plik ma starszą datę weryfikacji (${incoming.verifiedOn}) niż aktywne dane (${active.verifiedOn}).")
        return ImportDecision.Accept
    }
}
