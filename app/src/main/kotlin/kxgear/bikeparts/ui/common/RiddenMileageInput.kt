package kxgear.bikeparts.ui.common

private val kilometerPattern = Regex("\\d+(\\.\\d)?")

fun parseRiddenMileageInput(input: String): Int? {
    return parseKilometersInput(input)
}

fun parseKilometersInput(input: String): Int? {
    val normalized = input.trim()
    if (normalized.isEmpty()) {
        return 0
    }
    if (!kilometerPattern.matches(normalized)) {
        return null
    }
    val parts = normalized.split(".")
    val wholeKilometers = parts[0].toIntOrNull() ?: return null
    val tenths = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return wholeKilometers * 1000 + tenths * 100
}

const val RIDDEN_MILEAGE_ERROR_MESSAGE: String = "Ridden mileage must be a non-negative kilometer value with 0.1 km precision"
