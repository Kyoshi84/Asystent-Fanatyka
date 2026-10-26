// Czysty moduł Kotlin/JVM: model danych, silnik przepisów, kalendarz, pomiar, import danych.
// Nie zależy od Androida – testy uruchamia się szybko: ./gradlew :core:test
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    systemProperty("dataset.path", rootProject.file("app/src/main/assets/dataset/national.json").absolutePath)
}
