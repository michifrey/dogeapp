plugins {
    // AGP 9 has Kotlin built in (no org.jetbrains.kotlin.android plugin any more).
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
