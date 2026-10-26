-- Schemat bazy SQLite aplikacji Asystent Fanatyka (odpowiada encjom Room w app/src/main/java/pl/asystentfanatyka/data/db/Entities.kt).
-- Room generuje go automatycznie; pełny eksport po pierwszej kompilacji: app/schemas/pl.asystentfanatyka.data.db.AppDatabase/1.json
-- Dane przepisów są wersjonowane (datasetVersion). Aktywna jest jedna wersja (dataset_metadata.active = 1).

CREATE TABLE dataset_metadata (
    version          INTEGER NOT NULL PRIMARY KEY,
    datasetId        TEXT    NOT NULL,
    schemaVersion    INTEGER NOT NULL,
    generatedOn      TEXT    NOT NULL,          -- RRRR-MM-DD
    verifiedOn       TEXT    NOT NULL,          -- najstarsza data weryfikacji źródeł
    coverageFrom     TEXT    NOT NULL,          -- od kiedy zbiór reguł jest kompletny
    coverageNote     TEXT    NOT NULL,
    demo             INTEGER NOT NULL,          -- 1 = dane demonstracyjne
    active           INTEGER NOT NULL,
    origin           TEXT    NOT NULL,          -- asset | file | url:<adres>
    importedAtMillis INTEGER NOT NULL
);

CREATE TABLE regulation_source (
    datasetVersion INTEGER NOT NULL,
    id             TEXT    NOT NULL,
    title          TEXT    NOT NULL,
    reference      TEXT    NOT NULL,            -- np. „Dz.U. 2023 poz. 1373”
    url            TEXT,
    type           TEXT    NOT NULL,            -- LEGAL_ACT | DISTRICT_REGULATION | WATER_REGULATION | SECONDARY
    publishedOn    TEXT,
    verifiedOn     TEXT,
    notes          TEXT,
    PRIMARY KEY (datasetVersion, id)
);

CREATE TABLE area (
    datasetVersion INTEGER NOT NULL,
    code           TEXT    NOT NULL,            -- np. WISLA_PON_WLOCLAWKA
    name           TEXT    NOT NULL,
    description    TEXT    NOT NULL,
    implies        TEXT    NOT NULL,            -- kody obszarów nadrzędnych, rozdzielone przecinkami
    PRIMARY KEY (datasetVersion, code)
);

CREATE TABLE fish_species (
    datasetVersion    INTEGER NOT NULL,
    id                TEXT    NOT NULL,
    namePl            TEXT    NOT NULL,
    nameLatin         TEXT    NOT NULL,
    features          TEXT    NOT NULL,         -- cechy rozpoznawcze, jedna w linii
    habitat           TEXT    NOT NULL,
    similarIds        TEXT    NOT NULL,
    waterTypes        TEXT    NOT NULL,         -- RIVER,LAKE,STREAM,POND,RESERVOIR,ESTUARY
    imageAsset        TEXT,
    invasive          INTEGER NOT NULL,
    descriptionSource TEXT    NOT NULL,
    PRIMARY KEY (datasetVersion, id)
);

CREATE TABLE protection_rule (
    datasetVersion    INTEGER NOT NULL,
    id                TEXT    NOT NULL,
    speciesId         TEXT    NOT NULL,
    kind              TEXT    NOT NULL,         -- MIN_LENGTH | MAX_LENGTH | CLOSED_SEASON | WEEKDAY_BAN | DAILY_LIMIT_COUNT | NO_RELEASE_INVASIVE | NO_KEEP
    scope             TEXT    NOT NULL,         -- NATIONAL | DISTRICT | WATER
    areaCode          TEXT,                     -- NULL = wszystkie wody; OTHER = pozostałe wody
    scopeRef          TEXT,                     -- id okręgu lub wody dla DISTRICT/WATER
    lengthCm          REAL,
    seasonStart       TEXT,                     -- MM-DD
    seasonEnd         TEXT,                     -- MM-DD, „02-29” = koniec lutego
    holidayShortening INTEGER NOT NULL,         -- § 7 ust. 2 rozp. 2023/1373
    bannedDays        TEXT    NOT NULL,         -- MONDAY..SUNDAY, przecinki
    dailyLimit        INTEGER,
    validFrom         TEXT    NOT NULL,
    validTo           TEXT,
    sourceId          TEXT    NOT NULL,
    sourceLocator     TEXT    NOT NULL,         -- np. „§ 6 ust. 1”
    verification      TEXT    NOT NULL,         -- VERIFIED | UNVERIFIED | DEMO
    verifiedOn        TEXT,
    note              TEXT,
    PRIMARY KEY (datasetVersion, id)
);
CREATE INDEX index_protection_rule_datasetVersion_speciesId ON protection_rule (datasetVersion, speciesId);

-- Przygotowane pod moduł wyboru łowiska (poza zakresem tego MVP; tabele puste).
CREATE TABLE fishing_district (
    id                   TEXT NOT NULL PRIMARY KEY,
    name                 TEXT NOT NULL,
    voivodeship          TEXT NOT NULL,
    regulationSourceId   TEXT,
    regulationVerifiedOn TEXT
);
CREATE TABLE fishing_water (
    id                  TEXT NOT NULL PRIMARY KEY,
    name                TEXT NOT NULL,
    districtId          TEXT REFERENCES fishing_district(id) ON DELETE SET NULL,
    waterType           TEXT NOT NULL,
    areaCodes           TEXT NOT NULL,
    regulationAvailable INTEGER NOT NULL,
    regulationSourceId  TEXT,
    favorite            INTEGER NOT NULL
);
CREATE INDEX index_fishing_water_districtId ON fishing_water (districtId);

-- Dane użytkownika
CREATE TABLE catch_record (
    id                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    createdAtMillis   INTEGER NOT NULL,
    catchDate         TEXT    NOT NULL,
    speciesId         TEXT    NOT NULL,
    speciesName       TEXT    NOT NULL,
    speciesConfirmed  INTEGER NOT NULL,
    photoPath         TEXT,
    verdict           TEXT    NOT NULL,         -- NOT_ALLOWED | MEETS_CHECKED | CANNOT_CONFIRM
    justification     TEXT    NOT NULL,
    requiredMin       TEXT    NOT NULL,
    closedSeason      TEXT    NOT NULL,
    checked           TEXT    NOT NULL,
    notVerified       TEXT    NOT NULL,
    areaDescription   TEXT    NOT NULL,
    datasetVersion    INTEGER NOT NULL,
    datasetVerifiedOn TEXT    NOT NULL,
    sourceIds         TEXT    NOT NULL,
    note              TEXT
);
CREATE TABLE measurement_result (
    id            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    catchId       INTEGER NOT NULL REFERENCES catch_record(id) ON DELETE CASCADE,
    lengthCm      REAL    NOT NULL,
    uncertaintyCm REAL    NOT NULL,
    method        TEXT    NOT NULL,             -- MAT_READING | PHOTO_CALIBRATED | MANUAL_ESTIMATE
    reliability   TEXT    NOT NULL,             -- RELIABLE | LOW | UNRELIABLE
    warnings      TEXT    NOT NULL,
    pointsJson    TEXT
);
CREATE INDEX index_measurement_result_catchId ON measurement_result (catchId);
CREATE TABLE recognition_result (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    catchId            INTEGER NOT NULL REFERENCES catch_record(id) ON DELETE CASCADE,
    source             TEXT    NOT NULL,        -- ON_DEVICE_MODEL | REMOTE_API | IDENTIFICATION_KEY | USER_SELECTION
    candidates         TEXT    NOT NULL,
    confirmedSpeciesId TEXT    NOT NULL,
    needsBetterPhoto   INTEGER NOT NULL
);
CREATE INDEX index_recognition_result_catchId ON recognition_result (catchId);
