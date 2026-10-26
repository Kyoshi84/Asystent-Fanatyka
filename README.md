# Asystent Fanatyka – asystent wędkarza (Android, MVP)

Aplikacja dla wędkarzy łowiących w polskich wodach śródlądowych: kalendarz okresów ochronnych, karta gatunku, rozpoznawanie gatunku (klucz cech; interfejs pod model AI), pomiar długości (mata lub zdjęcie z kalibracją skali) oraz kontrola „Czy mogę zatrzymać tę rybę?” z trzema wynikami A / B / C. Działa offline.

> Moduł V „Wybór łowiska” został pominięty zgodnie z decyzją zlecającego. Baza i silnik reguł obsługują już przepisy okręgów i łowisk.

Analiza (Etap 1): [`docs/ANALIZA.md`](docs/ANALIZA.md) · format danych: [`docs/FORMAT_DANYCH.md`](docs/FORMAT_DANYCH.md) · AI: [`docs/ROZPOZNAWANIE_AI.md`](docs/ROZPOZNAWANIE_AI.md) · schemat SQL: [`docs/schema.sql`](docs/schema.sql), dane SQL: [`docs/seed.sql`](docs/seed.sql)

## Uruchomienie w Android Studio

1. Android Studio Meerkat (2024.3.1) lub nowsze, JDK 17+ (wbudowany JBR wystarcza), Android SDK 35.
2. *File → Open* → katalog `Asystent_Fanatyka`. Studio pobierze Gradle 8.14.3 (wrapper) i zależności.
3. Podłącz telefon z Androidem 8.0+ (API 26) z włączonym debugowaniem USB lub uruchom emulator.
4. Wybierz konfigurację **app** → *Run*.

Z wiersza poleceń:

```bash
./gradlew :core:test            # testy logiki (silnik przepisów, kalendarz, pomiar, import)
./gradlew :app:testDebugUnitTest # testy mapowania bazy
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedAndroidTest # testy Room i UI na urządzeniu/emulatorze
```

**Klucze API: nie są wymagane.** Aplikacja nie korzysta z żadnych płatnych usług. Internet jest potrzebny tylko do opcjonalnej aktualizacji danych z adresu ustawionego przez użytkownika.

## Struktura

```
core/                       czysty Kotlin – logika bez Androida
  model/ rules/ time/ measurement/ recognition/ data/
app/src/main/java/pl/asystentfanatyka/
  data/        Room (wersjonowane przepisy), repozytoria, DataStore, dziennik
  sync/        import z pliku i z HTTPS, atomowa podmiana danych
  camera/      CameraX, Photo Picker, przycinanie, zdjęcia w pamięci prywatnej
  recognition/ interfejs modelu AI + ekran klucza cech
  measurement/ ekran pomiaru z nakładką i lupą
  regulations/ formularz kontroli i wynik A/B/C
  ui/          motyw, start, gatunki, kalendarz, zapisane, ustawienia
app/src/main/assets/dataset/national.json   dane początkowe (generowane)
data/source/                rules.csv, species.json, meta.json – edytowalne źródła danych
tools/build_dataset.py      generator JSON + SQL z data/source
tools/compile_core.sh       kompilacja i testy :core bez sieci (kompilator z dystrybucji Gradle)
```

## Zależności

Kotlin 2.0.21, AGP 8.9.1, Gradle 8.14.3 · Jetpack Compose (BOM 2024.12.01), Material 3, Material Icons Extended · Navigation Compose 2.8.5 · Lifecycle 2.8.7 · Room 2.6.1 (KSP) · DataStore Preferences 1.1.1 · CameraX 1.4.1 · ExifInterface 1.3.7 · kotlinx-serialization-json 1.7.3 (tylko parser drzewa JSON) · Coroutines 1.9.0. Testy: JUnit 4, AndroidX Test, Compose UI Test, Room Testing.

Świadomie pominięte: Hilt (ręczne DI wystarcza), Retrofit/Ktor (jedno pobranie pliku), OpenCV i biblioteki ML (brak modelu i segmentacji w MVP).

## Co działa, a co nie

| Funkcja | Stan |
|---|---|
| Baza: 41 gatunków, 63 reguły krajowe z Dz.U. 2023 poz. 1373, każda ze źródłem, paragrafem, datami i statusem weryfikacji | gotowe |
| Kalendarz miesięczny, lista chronionych w dniu, wybór daty połowu, filtr typu wody, obszary z przepisów | gotowe |
| Okresy przez koniec roku, „koniec lutego”, skracanie o dni wolne (§ 7 ust. 2), zakazy w dni tygodnia (łosoś, troć) | gotowe |
| Wyszukiwarka i karta gatunku (cechy, podobne gatunki, przepisy, źródła) | gotowe; zamiast zdjęć poglądowych rysunek ogólny (brak zdjęć z pewną licencją) |
| Aparat (CameraX), galeria (Photo Picker), przycinanie, zdjęcia tylko lokalnie | gotowe |
| Rozpoznawanie: klucz cech offline dla wszystkich gatunków, wybór z listy, ręczne potwierdzenie | gotowe |
| Rozpoznawanie przez model AI | **niedostępne** – interfejs i polityka niepewności gotowe, modelu brak (patrz docs/ROZPOZNAWANIE_AI.md) |
| Pomiar: odczyt z maty; zdjęcie z kalibracją (wzorzec, pysk–ogon, punkt dla wygiętej ryby, lupa, niepewność, ocena jakości) | gotowe |
| Automatyczne wykrywanie ryby na zdjęciu, korekta perspektywy | **niedostępne** – opisane w interfejsie |
| Kontrola A/B/C z uzasadnieniem, listą sprawdzonych i niesprawdzonych warunków, źródłami | gotowe |
| Limity dobowe | krajowy limit węgorza; limity lokalne – po dodaniu regulaminów |
| Regulaminy okręgów PZW / łowisk, wybór łowiska, GPS | **poza zakresem** (moduł V pominięty); silnik i schemat gotowe |
| Import danych z pliku i adresu HTTPS, walidacja, atomowa podmiana, przywracanie poprzedniej wersji, ostrzeżenie o nieaktualnych danych | gotowe |
| Zapisane połowy, dziennik zdarzeń, ustawienia prywatności zdjęć | gotowe |

### Jak aplikacja wydaje wynik

- **A – Nie spełnia wymagań**: potwierdzony zakaz z reguły `VERIFIED` (np. okres ochronny, ryba nie przekracza wymiaru, limit).
- **B – Spełnia sprawdzone wymagania**: wszystkie właściwe reguły sprawdzone i spełnione, gatunek potwierdzony, pomiar wiarygodny, dane aktualne **oraz** wędkarz oświadczył, że sprawdził regulamin łowiska. Ekran wymienia, co dokładnie sprawdzono.
- **C – Nie można potwierdzić**: brak regulaminu łowiska, niepewny gatunek lub pomiar, pomiar w granicach niepewności wokół wymiaru, wynik zależny od nieznanego obszaru, dane nieaktualne/demonstracyjne, przepis niezweryfikowany.

Uwaga interpretacyjna: § 6 ust. 2 rozporządzenia dopuszcza połów ryb **powyżej** wymiaru ochronnego – szczupak 45,0 cm nie spełnia wymogu 45 cm.

## Testy – stan faktyczny

Uruchomione w środowisku budowy (bez Android SDK, kompilatorem Kotlin 2.0.21):
- `core` – **65 testów, wszystkie zaliczone** (silnik reguł: wszystkie przypadki z briefu, w tym granica wymiaru, okres przez koniec roku, dwa łowiska, brak regulaminu, nieaktualna baza, niepewny gatunek i pomiar, górny wymiar, limit dobowy; kalendarz i święta; pomiar i jakość zdjęcia; parser, walidator i polityka importu; zgodność rzeczywistego pliku danych z rozporządzeniem; spójność klucza cech z bazą).
- `app` – `MappersTest` (2 testy) **zaliczony** po kompilacji z atrapami adnotacji Room.
- Warstwa danych i synchronizacji została sprawdzona kompilatorem z atrapami API Androida.

**Nieuruchomione** (wymagają Android SDK/urządzenia): pełna kompilacja aplikacji, ekrany Compose, `DatasetDaoTest` (atomowość importu w Room), `BasicFlowUiTest`. Pierwszy build w Android Studio może wymagać drobnych poprawek, jeśli któraś biblioteka zmieniła API.

## Aktualizacja przepisów

1. Edytuj `data/source/rules.csv` (i ewentualnie `species.json`, `meta.json`), ustaw `verification` i `verifiedOn`, podnieś `metadata.version`.
2. `python3 tools/build_dataset.py`
3. Nowa wersja aplikacji zainstaluje dane automatycznie; istniejącą instalację zaktualizujesz importem pliku w Ustawieniach lub z własnego adresu HTTPS.

## Plan dalszego rozwoju

1. Moduł wyboru łowiska: tabela okręgów PZW i wód, import regulaminów (RAPR) jako reguły `DISTRICT`/`WATER`, ulubione łowiska, potem GPS i mapa.
2. Zdjęcia poglądowe gatunków z licencją CC (np. Wikimedia Commons) z atrybucją.
3. Zbiór zdjęć i model TFLite, następnie wariant hybrydowy z API (za zgodą).
4. Pomiar: wykrywanie maty/znacznika ArUco i korekta perspektywy, segmentacja ryby jako podpowiedź punktów.
5. Gatunki chronione na podstawie ustawy o ochronie przyrody, raki, wody morskie.
6. Repozytorium danych z historią zmian i podpisem plików aktualizacji.

Aplikacja jest pomocą, nie wykładnią prawa. Odpowiedzialność za przestrzeganie przepisów ponosi wędkarz.
