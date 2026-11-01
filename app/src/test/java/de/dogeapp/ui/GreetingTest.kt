package de.dogeapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun greetsByTimeOfDay() {
        assertEquals("Guten Morgen, Yara!", greetingText("Yara", 8))
        assertEquals("Hallo, Yara!", greetingText(" Yara ", 14))
        assertEquals("Guten Abend, Yara!", greetingText("Yara", 19))
        assertEquals("Gute Nacht, Yara!", greetingText("Yara", 23))
        assertEquals("Hallo!", greetingText("", 12))
    }
}
