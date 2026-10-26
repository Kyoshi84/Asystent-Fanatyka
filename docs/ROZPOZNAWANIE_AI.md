# Rozpoznawanie gatunku przez model AI – wymagania i podłączenie

**Stan w MVP:** model nie jest dołączony. Aplikacja pokazuje to wprost (`NotConfiguredRecognizer`) i prowadzi użytkownika kluczem cech. Nic nie udaje działającego AI.

## Wymagania dla modelu

- Klasy: identyfikatory gatunków z `species.json` (41) + klasa „nie ryba / nieczytelne”.
- Dane: min. 500–1000 zdjęć na gatunek, z różnych wód, pór roku, wielkości ryb, z ręki i z maty; osobno zweryfikowane przez ichtiologa dla par: kleń/jaź/jelec, leszcz/krąp/rozpiór, płoć/wzdręga, łosoś/troć/troć jeziorowa, pstrąg potokowy/tęczowy, sieja/sielawa, karp/karaś. Licencje zdjęć muszą pozwalać na trening.
- Podział danych według **łowiska i autora** (nie losowo po zdjęciach), aby uniknąć zawyżonej dokładności.
- Kalibracja wyników (np. temperature scaling) – tylko wtedy prezentujemy procenty. Bez kalibracji `probability = null`.
- Metryki do raportu: dokładność top-1/top-3 na zbiorze testowym, macierz pomyłek dla par powyżej, odsetek odrzuceń przy progu 0,80.

## Podłączenie modelu lokalnego (TFLite / LiteRT)

1. Dodaj zależność `com.google.ai.edge.litert:litert` (lub `org.tensorflow:tensorflow-lite`) i plik `app/src/main/assets/model/fish.tflite` + `labels.txt`.
2. Zaimplementuj `SpeciesRecognizer`:
   - przeskaluj bitmapę do wejścia modelu, uruchom interpreter na `Dispatchers.Default`,
   - zwróć `RecognitionOutcome(ON_DEVICE_MODEL, kandydaci posortowani, needsBetterPhoto = ocena jakości lub klasa „nieczytelne”)`.
3. Podmień `recognizer` w `AppContainer`.
4. Ekran rozpoznawania sam pokaże przycisk „Rozpoznaj na zdjęciu”, kandydatów i wymusi potwierdzenie zgodnie z `RecognitionPolicy`.

## Zewnętrzne API (opcjonalnie)

Implementacja z `sendsPhotoOffDevice = true` musi: pytać o zgodę przy pierwszym użyciu, wysyłać tylko przycięte i zmniejszone zdjęcie bez EXIF, nie przechowywać klucza API w kodzie (klucz po stronie własnego serwera pośredniczącego). Wynik nadal wymaga potwierdzenia gatunku przez użytkownika.

## Zasada niezmienna

O legalności zatrzymania ryby decyduje wyłącznie `RuleEngine` na podstawie zweryfikowanych reguł. Model AI może tylko zaproponować gatunek.
