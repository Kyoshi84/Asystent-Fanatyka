#!/usr/bin/env bash
# Kompilacja i testy modułu :core bez dostępu do sieci, z użyciem kompilatora Kotlin dołączonego do Gradle.
# W Android Studio używaj zwykłego: ./gradlew :core:test
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
L="${GRADLE_LIB:-/opt/gradle-8.14.3/lib}"
OUT="${OUT:-$ROOT/build-local}"
KC="$L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/kotlin-script-runtime-2.0.21.jar:$L/kotlin-reflect-2.0.21.jar:$L/kotlin-daemon-embeddable-2.0.21.jar:$L/trove4j-1.0.20200330.jar:$L/annotations-24.0.1.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar"
LIBS="$L/kotlin-stdlib-2.0.21.jar:$L/kotlinx-serialization-core-jvm-1.6.2.jar:$L/kotlinx-serialization-json-jvm-1.6.2.jar"
JUNIT="$L/junit-4.13.2.jar:$(ls $L/hamcrest-core-*.jar 2>/dev/null | head -1)"
rm -rf "$OUT" && mkdir -p "$OUT/main" "$OUT/test"
kc() { java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@" 2>&1 | grep -v '^Picked up' || true; }
echo "== Kompilacja core/src/main"
kc -cp "$LIBS" -d "$OUT/main" "$ROOT/core/src/main/kotlin"
test -f "$OUT/main/pl/asystentfanatyka/core/rules/RuleEngine.class" || { echo "BŁĄD kompilacji main"; exit 1; }
echo "== Kompilacja core/src/test"
kc -cp "$LIBS:$JUNIT:$OUT/main" -d "$OUT/test" "$ROOT/core/src/test/kotlin"
CLASSES=$(cd "$OUT/test" && find . -name '*Test.class' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
test -n "$CLASSES" || { echo "BŁĄD kompilacji testów"; exit 1; }
echo "== Testy"
java -Ddataset.path="$ROOT/app/src/main/assets/dataset/national.json" -cp "$LIBS:$JUNIT:$OUT/main:$OUT/test" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v '^Picked up'
