package kxgear.bikeparts.ui.common

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class MileageFormatterTest {
    @Test
    fun formatMetersAsKilometersUsesOneDecimalPlace() {
        assertEquals("100.0km", formatMetersAsKilometers(100_000))
        assertEquals("2.5km", formatMetersAsKilometers(2_500))
        assertEquals("0.0km", formatMetersAsKilometers(1))
    }

    @Test
    fun formatMetersUsesMeterUnit() {
        assertEquals("100000m", formatMeters(100_000))
        assertEquals("2500m", formatMeters(2_500))
        assertEquals("1m", formatMeters(1))
    }

    @Test
    fun parseRiddenMileageInputReadsKilometersWithOneDecimalPlace() {
        assertEquals(100_000, parseRiddenMileageInput("100.0"))
        assertEquals(2_500, parseRiddenMileageInput("2.5"))
        assertEquals(10_100, parseRiddenMileageInput("10.1"))
        assertEquals(10_900, parseRiddenMileageInput("10.9"))
        assertEquals(2_000, parseRiddenMileageInput("2"))
        assertEquals(0, parseRiddenMileageInput(""))
    }

    @Test
    fun parseRiddenMileageInputRejectsMoreThanOneDecimalPlace() {
        assertEquals(null, parseRiddenMileageInput("2.55"))
        assertEquals(null, parseRiddenMileageInput("2.05"))
        assertEquals(null, parseRiddenMileageInput("abc"))
    }

    @Test
    fun parseKilometersInputReadsBikeMileageInKilometers() {
        assertEquals(100_000, parseKilometersInput("100.0"))
        assertEquals(2_500, parseKilometersInput("2.5"))
        assertEquals(10_100, parseKilometersInput("10.1"))
        assertEquals(10_900, parseKilometersInput("10.9"))
    }

    @Test
    fun formatCreatedDateUsesDayMonthYear() {
        assertEquals("16.04.26", formatCreatedDate(1_776_368_847_444L, ZoneOffset.UTC))
    }
}
