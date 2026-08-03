// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9's built-in Kotlin support bundles Kotlin Gradle Plugin 2.2.10 by default --
// override to the newer Kotlin release used project-wide (see
// https://kotl.in/gradle/agp-built-in-kotlin).
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.0" apply false
    // org.jetbrains.kotlin.android is intentionally not declared -- AGP 9 has built-in
    // Kotlin support and applying it explicitly is now an error (see app/build.gradle.kts).
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}