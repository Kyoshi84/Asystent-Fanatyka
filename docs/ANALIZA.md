# Asystent Fanatyka – Etap 1: analiza i projekt

Stan na 26.10.2026. Zakres: wszystkie moduły z briefu **z wyjątkiem modułu V „Wybór łowiska”** (pominięty na życzenie). Model danych i silnik reguł obsługują już okręgi i łowiska, więc moduł można dodać bez zmiany logiki.

## 1. Źródła przepisów – wyniki weryfikacji

| Źródło | Status | Użycie |
|---|---|---|
| Rozporządzenie MRiRW z 12.07.2023 w sprawie szczegółowych warunków ochrony i połowu ryb w powierzchniowych wodach śródlądowych, **Dz.U. 2023 poz. 1373** (w życiu od 27.07.2023) | Tekst sprawdzony w ELI (eli.gov.pl) 26.10.2026; nie znaleziono aktów zmieniających | Jedyna podstawa reguł w bazie (63 reguły, `VERIFIED`) |
| Ustawa z 18.01.1951 o dniach wolnych od pracy | Wiedza o świętach; Wigilia wolna od 2025 r. potwierdzona w wyszukiwarce | Skracanie okresów ochronnych (§ 7 ust. 2) |
| Artykuł PZW 969.pzw.pl (z briefu) | **Strona blokuje automatyczne pobieranie (robots.txt)** – nie odczytano treści | Nie stanowi podstawy żadnej reguły; zapisany jako źródło wtórne |
| Regulaminy amatorskiego połowu ryb okręgów PZW / Wód Polskich | Różnią się między okręgami i latami | **Nie wprowadzono** – brak modułu łowisk; aplikacja uczciwie pokazuje „regulamin łowiska niesprawdzony” |

Najważniejsze ustalenia z rozporządzenia, które wpływają na logikę:

- **§ 6 ust. 2**: wymiar ochronny to długość, „powyżej” której połów jest dozwolony. Ryba dokładnie 45,0 cm (szczupak 45 cm) **nie** spełnia wymogu. Wiele artykułów popularnych upraszcza to do „od 45 cm” – aplikacja stosuje brzmienie przepisu.
- Długość: od początku zamkniętego pyska do końca najdłuższego promienia płetwy ogonowej.
- **§ 7 ust. 2**: jeśli pierwszy lub ostatni dzień okresu ochronnego przypada na dzień ustawowo wolny od pracy, okres skraca się o ten dzień (nie dotyczy jesiotra ostronosego). Przykład: szczupak w 2026 r. chroniony od 2.01 (1.01 to święto) do 30.04.
- Przepisy zależne od obszaru: pstrąg potokowy (dorzecze Wisły do Sanu, Odry do Bystrzycy vs pozostałe), miętus (Odra poniżej Warty), certa, łosoś i troć (Wisła powyżej/poniżej zapory we Włocławku – również zakazy połowu w wybrane dni tygodnia).
- **§ 2 ust. 3**: dobowy limit węgorza – 2 szt. Innych limitów krajowych dla wędkarzy rozporządzenie nie ustala; wprowadzają je regulaminy gospodarzy wód.
- **§ 8**: gatunki nierodzime (babki, czebaczek amurski, sumiki, trawianka) – nie wolno ich wypuszczać.
- Przepisy nie obejmują gatunków chronionych na podstawie ustawy o ochronie przyrody – te nie są w bazie (zaznaczone w opisie zbioru).

## 2. Architektura

```
Asystent_Fanatyka/
├── core/   czysty Kotlin/JVM – bez Androida, testowany szybko i w całości
│   ├── model/        FishSpecies, ProtectionRule, RegulationSource, Area, DatasetMetadata
│   ├── rules/        RuleEngine (A/B/C), CheckInput/CheckResult
│   ├── time/         PolishHolidays, SeasonCalculator (przejście przez koniec roku, „koniec lutego”)
│   ├── measurement/  MeasurementCalculator (kalibracja, niepewność), ImageQuality
│   ├── recognition/  IdentificationKey (klucz cech), RecognitionPolicy
│   └── data/         DatasetParser + DatasetValidator (JSON), ImportPolicy
└── app/    Android: Jetpack Compose, Material 3, MVVM
    ├── data/         Room (wersjonowane dane), repozytoria, DataStore, dziennik zdarzeń
    ├── sync/         import z pliku / adresu HTTPS, atomowa podmiana
    ├── camera/       CameraX, Photo Picker, przycinanie, przechowywanie zdjęć
    ├── recognition/  interfejs SpeciesRecognizer + ekran z kluczem cech
    ├── measurement/  ekran pomiaru z nakładką, lupą i oceną wiarygodności
    ├── regulations/  formularz kontroli i ekran wyniku
    └── ui/           motyw, nawigacja, start, gatunki, kalendarz, zapisane, ustawienia
```

Decyzje:
- **Silnik reguł w `core`** – deterministyczny, bez AI, w pełni pokryty testami. Model AI może co najwyżej zaproponować gatunek; gatunek musi potwierdzić użytkownik.
- **Ręczne DI** (`AppContainer`) zamiast Hilt – kilka zależności, Hilt nie jest uzasadniony.
- **Bez Retrofit/Ktor** – jedno pobranie pliku JSON obsługuje `HttpURLConnection`.
- **Bez OpenCV w MVP** – patrz pkt 4.
- Lokalizacja (GPS) pominięta razem z modułem łowisk.

## 3. Rozpoznawanie gatunku – porównanie i wybór

| Kryterium | Model na telefonie (TFLite/LiteRT) | Zewnętrzne API | Hybryda |
|---|---|---|---|
| Koszt | jednorazowy: zebranie danych i trening; brak kosztu za zapytanie | opłata za zapytanie, utrzymanie klucza | oba |
| Dokładność dla par kleń/jaź, leszcz/krąp, łosoś/troć | zależy od własnego zbioru; ogólne modele słabe | ogólne API rozpoznaje „rybę”, rzadko gatunek polskich ryb słodkowodnych | najlepsza możliwa |
| Prywatność | zdjęcie nie opuszcza telefonu | zdjęcie wysyłane na serwer | wysyłka tylko za zgodą |
| Sprzęt | 5–20 MB modelu, ~100 ms na nowszych telefonach | dowolny | jak lokalny |
| Offline | tak | nie | częściowo |

**Wybór dla MVP**: brak udawanego modelu. Nie istnieje dostępny, zweryfikowany model rozróżniający polskie gatunki słodkowodne, a wytrenowanie go wymaga tysięcy opisanych zdjęć na gatunek. Aplikacja ma:
- interfejs `SpeciesRecognizer` z polityką niepewności (`RecognitionPolicy`: próg 0,80, margines 0,25, pary łatwe do pomylenia zawsze do potwierdzenia),
- implementację `NotConfiguredRecognizer`, która wprost informuje, że modelu nie ma,
- **działający offline klucz cech** (pytania o wąsiki, płetwę tłuszczową, kształt płetwy odbytowej itd.), obejmujący wszystkie 41 gatunków bazy – test sprawdza spójność klucza z bazą,
- wybór z listy i ręczne potwierdzenie.

Docelowo: hybryda – lokalny model TFLite + opcjonalne API po zgodzie. Instrukcja: `docs/ROZPOZNAWANIE_AI.md`.

## 4. Pomiar długości – metoda i ograniczenia

Jedno zdjęcie bez wzorca skali **nie** pozwala ustalić długości. Wdrożone metody:

1. **Odczyt z maty / miarki (metoda podstawowa)** – użytkownik wpisuje odczyt; niepewność = połowa działki (1 cm → ±0,5 cm).
2. **Zdjęcie z kalibracją skali** – użytkownik przeciąga dwa punkty na podziałce (znana odległość) oraz punkty pyska i końca płetwy ogonowej (opcjonalnie punkt środkowy dla wygiętej ryby). Lupa ×3 pod palcem pozwala trafić w kreskę.

Niepewność liczona jest jawnie: błąd wskazania punktu (domyślnie 0,35% dłuższego boku zdjęcia na punkt) dla wzorca i ryby + składnik geometryczny (1% gdy mata leży wzdłuż ryby, 3% dla przedmiotu referencyjnego, kary za krótki wzorzec, brak równoległości, odległość wzorca od ryby, wygięcie, mały kadr, punkty przy krawędzi). Wiarygodność: ≤3% dobra, ≤8% niska, powyżej – niewystarczająca. Ocena jakości zdjęcia: ostrość (wariancja laplasjanu), naświetlenie, odblaski.

Czego nie ma i dlaczego:
- **Korekta perspektywy** – wymagałaby wykrycia płaszczyzny maty (np. 4 narożniki lub znacznik ArUco). Zamiast tego aplikacja wymaga zdjęcia prostopadle z góry i ostrzega przy sygnałach złej geometrii.
- **Automatyczne wykrywanie ryby** – segmentacja (np. model DeepLab/YOLO-seg) nie jest dołączona; punkty wskazuje się ręcznie. To uczciwie opisano w interfejsie.

Silnik reguł uwzględnia niepewność: ryba jest „powyżej wymiaru” tylko gdy `długość − niepewność > wymiar`; wynik w przedziale niepewności daje stan C.

## 5. Model danych

Encje (Room, `docs/schema.sql`): `dataset_metadata`, `regulation_source`, `area`, `fish_species`, `protection_rule`, `fishing_district`, `fishing_water` (pod przyszły moduł łowisk), `catch_record`, `measurement_result`, `recognition_result`. Odpowiedniki z briefu: `FishSpecies`, `ProtectionRule`, `FishingWater`, `FishingDistrict`, `RegulationSource`, `MeasurementResult`, `RecognitionResult`, `DatabaseSyncMetadata` (= `dataset_metadata`).

Każda reguła ma: rodzaj (`MIN_LENGTH`, `MAX_LENGTH`, `CLOSED_SEASON`, `WEEKDAY_BAN`, `DAILY_LIMIT_COUNT`, `NO_RELEASE_INVASIVE`, `NO_KEEP`), zasięg (`NATIONAL`/`DISTRICT`/`WATER`), obszar, daty obowiązywania `validFrom`/`validTo`, źródło z miejscem w akcie, status weryfikacji i datę weryfikacji. Jeden gatunek ma wiele reguł zależnych od obszaru, łowiska i daty.

## 6. Aktualizacja i weryfikacja przepisów

- Dane źródłowe: `data/source/` (`rules.csv`, `species.json`, `meta.json`) → `python3 tools/build_dataset.py` → `app/src/main/assets/dataset/national.json` i `docs/seed.sql`.
- Import w aplikacji: plik JSON (Ustawienia → Importuj) lub własny adres HTTPS (sprawdzany najwyżej raz na dobę). Brak założenia istnienia API.
- Każdy plik przechodzi pełną walidację (`DatasetValidator`) i politykę (`ImportPolicy`: tylko wyższa wersja przy aktualizacji, dane demonstracyjne nie zastępują zweryfikowanych, starsza data weryfikacji odrzucana). Zapis i przełączenie w **jednej transakcji**; zachowywana jest poprzednia wersja (przycisk „Przywróć poprzednią wersję”).
- Dane starsze niż próg (domyślnie 180 dni) są oznaczane jako nieaktualne i blokują stan A/B.
- Reguła bez potwierdzonego źródła → `UNVERIFIED` → nigdy nie daje stanu A ani B.

## 7. Zakres MVP

W zakresie: kalendarz ochronny, baza 41 gatunków i 63 reguł krajowych, karta gatunku, wyszukiwarka, aparat/galeria/przycinanie, rozpoznawanie kluczem cech (model AI – interfejs bez modelu), pomiar (mata + zdjęcie z kalibracją), kontrola A/B/C, zapisane połowy, ustawienia i źródła, import/aktualizacja danych.

Poza zakresem: moduł wyboru łowiska (na życzenie), regulaminy okręgów PZW, GPS i mapa, wytrenowany model AI, automatyczna segmentacja ryby, korekta perspektywy, raki, wody morskie.

## 8. Ryzyka

1. Zmiana rozporządzenia – ograniczone przez datę weryfikacji, próg nieaktualności i aktualizację z pliku.
2. Interpretacja „powyżej wymiaru” – przyjęto dosłowne brzmienie § 6 ust. 2; łatwe do zmiany w jednym miejscu (`RuleEngine.evaluate`).
3. Brak regulaminów lokalnych – stan B jest możliwy tylko po wyraźnym oświadczeniu wędkarza, że sprawdził regulamin; w przeciwnym razie C.
4. Błędna identyfikacja gatunku – wymagane ręczne potwierdzenie; pary łatwe do pomylenia mają wskazówki różnicujące.
5. Błędy pomiaru – jawna niepewność, stan C w pobliżu wymiaru.
