package kxgear.bikeparts.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatMetersAsKilometers(meters: Int): String {
    return "${formatMetersAsKilometersValue(meters)}km"
}

fun formatMetersAsKilometersValue(meters: Int): String {
    return String.format(Locale.US, "%.1f", meters / 1000.0)
}

fun formatMeters(meters: Int): String {
    return "${meters}m"
}

fun formatCreatedDate(
    timestamp: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String =
    CreatedDateFormatter.format(
        Instant.ofEpochMilli(timestamp).atZone(zoneId),
    )

private val CreatedDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yy", Locale.US)
