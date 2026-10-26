# Format pliku danych przepisów (schemaVersion 1)

Plik JSON importowany przez aplikację (Ustawienia → Importuj plik danych) lub pobierany z adresu HTTPS. Najprościej przygotować go z plików w `data/source/`:

```bash
python3 tools/build_dataset.py            # → app/src/main/assets/dataset/national.json + docs/seed.sql
```

## Struktura

```json
{
  "metadata": {
    "datasetId": "pl-krajowe-wody-srodladowe",
    "version": 2,                 // podnoś przy każdej zmianie – aktualizacje przyjmują tylko wyższą wersję
    "schemaVersion": 1,
    "generatedOn": "2026-10-26",
    "verifiedOn": "2026-10-26",   // najstarsza data weryfikacji źródeł
    "coverageFrom": "2023-07-27", // od kiedy reguły są kompletne
    "coverageNote": "…",
    "demo": false                 // true = dane demonstracyjne (nie mogą zawierać reguł VERIFIED)
  },
  "sources": [ { "id", "title", "reference", "url", "type", "publishedOn", "verifiedOn", "notes" } ],
  "areas":   [ { "code", "name", "description", "implies": ["KOD_NADRZEDNY"] } ],
  "species": [ { "id", "namePl", "nameLatin", "features": [], "habitat", "similar": [], "waterTypes": [], "invasive", "imageAsset" } ],
  "rules":   [ { … } ]
}
```

## Reguła

| Pole | Wymagane | Opis |
|---|---|---|
| `id` | tak | unikalny identyfikator |
| `speciesId` | tak | id gatunku z `species` |
| `kind` | tak | `MIN_LENGTH`, `MAX_LENGTH`, `CLOSED_SEASON`, `WEEKDAY_BAN`, `DAILY_LIMIT_COUNT`, `NO_RELEASE_INVASIVE`, `NO_KEEP` |
| `scope` | tak | `NATIONAL`, `DISTRICT` (okręg), `WATER` (łowisko) |
| `scopeRef` | dla DISTRICT/WATER | id okręgu lub wody |
| `areaCode` | nie | kod z `areas`; `OTHER` = „pozostałe wody” (wody spoza obszarów wymienionych w innych regułach tego gatunku) |
| `lengthCm` | MIN/MAX | MIN: zatrzymać można rybę **dłuższą** niż wartość; MAX: nie dłuższą niż wartość |
| `seasonStart`, `seasonEnd` | CLOSED_SEASON, WEEKDAY_BAN | `MM-DD`; koniec wcześniejszy niż początek = okres przez koniec roku; `02-29` = koniec lutego |
| `holidayShortening` | nie | `true` = § 7 ust. 2 (skrócenie o dzień wolny na początku/końcu) |
| `bannedDays` | WEEKDAY_BAN | np. `["FRIDAY","SATURDAY","SUNDAY"]` |
| `dailyLimit` | DAILY_LIMIT_COUNT | liczba sztuk na dobę |
| `validFrom`, `validTo` | `validFrom` tak | daty obowiązywania reguły (wersjonowanie) |
| `sourceId`, `sourceLocator` | tak | źródło i miejsce w akcie, np. `§ 6 ust. 1` |
| `verification` | tak | `VERIFIED`, `UNVERIFIED`, `DEMO` |
| `verifiedOn` | dla VERIFIED | data weryfikacji ze źródłem |

## Ręczne uzupełnianie (np. regulamin okręgu PZW)

1. Dodaj źródło w `data/source/meta.json` (`type: DISTRICT_REGULATION`, adres PDF regulaminu, data).
2. Dopisz wiersze w `data/source/rules.csv` z `scope=DISTRICT` i `scopeRef=<id okręgu>`. Jeśli nie masz pewności co do przepisu – `verification=UNVERIFIED` (silnik pokaże go, ale nie wyda na jego podstawie stanu A ani B).
3. Podnieś `metadata.version` i `metadata.verifiedOn`, uruchom `tools/build_dataset.py`, zaimportuj plik w aplikacji.

Reguły okręgów i wód zadziałają w pełni po dodaniu modułu wyboru łowiska (silnik i baza są gotowe; testy `RuleEngineTest` obejmują reguły lokalne).

## Walidacja

Plik jest odrzucany w całości, gdy: niepoprawny JSON, inna wersja schematu, zduplikowane identyfikatory, reguła wskazuje nieznany gatunek/źródło/obszar, brak pól wymaganych dla rodzaju reguły, reguła `VERIFIED` bez daty weryfikacji, reguła `VERIFIED` w zbiorze demonstracyjnym, `validTo` przed `validFrom`. Poprzednie dane pozostają wtedy aktywne.
