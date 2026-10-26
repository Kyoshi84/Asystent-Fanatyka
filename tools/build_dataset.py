#!/usr/bin/env python3
"""
Buduje plik danych aplikacji (JSON) z plików źródłowych w data/source/:
  meta.json     – metadane zbioru, źródła, obszary
  species.json  – gatunki
  rules.csv     – reguły (separator ';', kodowanie UTF-8)

Użycie:
  python3 tools/build_dataset.py [--out app/src/main/assets/dataset/national.json] [--sql docs/seed.sql]

Po każdej zmianie przepisów: popraw rules.csv, ustaw verification/verifiedOn,
podnieś metadata.version i metadata.verifiedOn w meta.json, uruchom skrypt.
Pełną walidację wykonuje aplikacja (DatasetParser + DatasetValidator) – plik
z błędami zostanie odrzucony w całości, a poprzednia baza pozostanie aktywna.
"""
import argparse
import csv
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "data", "source")

KINDS = {"MIN_LENGTH", "MAX_LENGTH", "CLOSED_SEASON", "WEEKDAY_BAN", "DAILY_LIMIT_COUNT", "NO_RELEASE_INVASIVE", "NO_KEEP"}


def load_rules(path):
    rules = []
    with open(path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f, delimiter=";")
        for n, row in enumerate(reader, start=2):
            if not row.get("id"):
                continue
            if row["kind"] not in KINDS:
                sys.exit(f"rules.csv:{n}: nieznany kind {row['kind']}")
            r = {
                "id": row["id"],
                "speciesId": row["speciesId"],
                "kind": row["kind"],
                "scope": row["scope"],
                "validFrom": row["validFrom"],
                "sourceId": row["sourceId"],
                "sourceLocator": row["sourceLocator"],
                "verification": row["verification"],
            }
            opt = {
                "areaCode": row.get("areaCode"),
                "scopeRef": row.get("scopeRef"),
                "seasonStart": row.get("seasonStart"),
                "seasonEnd": row.get("seasonEnd"),
                "validTo": row.get("validTo"),
                "verifiedOn": row.get("verifiedOn"),
                "note": row.get("note"),
            }
            for k, v in opt.items():
                if v:
                    r[k] = v
            if row.get("lengthCm"):
                r["lengthCm"] = float(row["lengthCm"].replace(",", "."))
            if row.get("dailyLimit"):
                r["dailyLimit"] = int(row["dailyLimit"])
            if row.get("holidayShortening"):
                r["holidayShortening"] = row["holidayShortening"].strip().lower() == "true"
            if row.get("bannedDays"):
                r["bannedDays"] = [d.strip() for d in row["bannedDays"].split(",") if d.strip()]
            rules.append(r)
    return rules


def sql_str(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def to_sql(data):
    """Skrypt INSERT zgodny ze schematem Room (docs/schema.sql). Kolumny jak pola encji."""
    m = data["metadata"]
    v = m["version"]
    out = ["-- Wygenerowano przez tools/build_dataset.py – dane początkowe zgodne ze schematem docs/schema.sql", "BEGIN TRANSACTION;"]
    out.append("UPDATE dataset_metadata SET active = 0;")
    out.append(
        "INSERT INTO dataset_metadata(version,datasetId,schemaVersion,generatedOn,verifiedOn,coverageFrom,coverageNote,demo,active,origin,importedAtMillis) VALUES ("
        + ",".join(sql_str(x) for x in [v, m["datasetId"], m["schemaVersion"], m["generatedOn"], m["verifiedOn"], m["coverageFrom"], m["coverageNote"], m["demo"], True, "sql", 0])
        + ");"
    )
    for s in data["sources"]:
        out.append("INSERT INTO regulation_source(datasetVersion,id,title,reference,url,type,publishedOn,verifiedOn,notes) VALUES ("
                   + ",".join(sql_str(x) for x in [v, s["id"], s["title"], s["reference"], s.get("url"), s["type"], s.get("publishedOn"), s.get("verifiedOn"), s.get("notes")]) + ");")
    for a in data["areas"]:
        out.append("INSERT INTO area(datasetVersion,code,name,description,implies) VALUES ("
                   + ",".join(sql_str(x) for x in [v, a["code"], a["name"], a["description"], ",".join(a.get("implies", []))]) + ");")
    for sp in data["species"]:
        out.append("INSERT INTO fish_species(datasetVersion,id,namePl,nameLatin,features,habitat,similarIds,waterTypes,imageAsset,invasive,descriptionSource) VALUES ("
                   + ",".join(sql_str(x) for x in [v, sp["id"], sp["namePl"], sp["nameLatin"], "\n".join(sp.get("features", [])), sp.get("habitat", ""),
                                                   ",".join(sp.get("similar", [])), ",".join(sp.get("waterTypes", [])), sp.get("imageAsset"), sp.get("invasive", False), sp.get("descriptionSource", "")]) + ");")
    for r in data["rules"]:
        out.append("INSERT INTO protection_rule(datasetVersion,id,speciesId,kind,scope,areaCode,scopeRef,lengthCm,seasonStart,seasonEnd,holidayShortening,bannedDays,dailyLimit,validFrom,validTo,sourceId,sourceLocator,verification,verifiedOn,note) VALUES ("
                   + ",".join(sql_str(x) for x in [v, r["id"], r["speciesId"], r["kind"], r["scope"], r.get("areaCode"), r.get("scopeRef"), r.get("lengthCm"),
                                                   r.get("seasonStart"), r.get("seasonEnd"), r.get("holidayShortening", False), ",".join(r.get("bannedDays", [])), r.get("dailyLimit"),
                                                   r["validFrom"], r.get("validTo"), r["sourceId"], r["sourceLocator"], r["verification"], r.get("verifiedOn"), r.get("note")]) + ");")
    out.append("COMMIT;")
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "app", "src", "main", "assets", "dataset", "national.json"))
    ap.add_argument("--sql", default=os.path.join(ROOT, "docs", "seed.sql"))
    args = ap.parse_args()

    with open(os.path.join(SRC, "meta.json"), encoding="utf-8") as f:
        meta = json.load(f)
    with open(os.path.join(SRC, "species.json"), encoding="utf-8") as f:
        species = json.load(f)
    for sp in species:
        sp.setdefault("descriptionSource", "Opis poglądowy cech rozpoznawczych – nie jest źródłem prawa.")
        sp.setdefault("imageAsset", None)
    rules = load_rules(os.path.join(SRC, "rules.csv"))

    ids = {s["id"] for s in species}
    missing = sorted({r["speciesId"] for r in rules} - ids)
    if missing:
        sys.exit(f"Reguły odwołują się do nieznanych gatunków: {missing}")

    data = {"metadata": meta["metadata"], "sources": meta["sources"], "areas": meta["areas"], "species": species, "rules": rules}
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    os.makedirs(os.path.dirname(args.sql), exist_ok=True)
    with open(args.sql, "w", encoding="utf-8") as f:
        f.write(to_sql(data))
    print(f"OK: {len(species)} gatunków, {len(rules)} reguł → {os.path.relpath(args.out, ROOT)}, {os.path.relpath(args.sql, ROOT)}")


if __name__ == "__main__":
    main()
