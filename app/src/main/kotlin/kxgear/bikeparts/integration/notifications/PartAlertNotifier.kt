package kxgear.bikeparts.integration.notifications

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.PlayBeepPattern
import kxgear.bikeparts.R
import kxgear.bikeparts.integration.logging.BikePartsLogger
import kxgear.bikeparts.ui.common.formatMetersAsKilometers

interface PartAlertNotifier {
    fun showAlert(
        bikeName: String,
        partName: String,
        alertText: String?,
        thresholdMeters: Int,
        currentMileageMeters: Int,
    )
}

class AndroidPartAlertNotifier(
    context: Context,
    private val logger: BikePartsLogger,
    private val karooSystem: KarooSystemService? = null,
) : PartAlertNotifier {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun showAlert(
        bikeName: String,
        partName: String,
        alertText: String?,
        thresholdMeters: Int,
        currentMileageMeters: Int,
    ) {
        val contentText =
            alertText?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "$partName reached ${formatMetersAsKilometers(thresholdMeters)} on $bikeName"

        mainHandler.post {
            runCatching {
                karooSystem?.dispatch(
                    InRideAlert(
                        id = "part-alert-$partName-$thresholdMeters-$currentMileageMeters",
                        icon = R.mipmap.ic_launcher,
                        title = partName,
                        detail = contentText,
                        autoDismissMs = 30_000L,
                        backgroundColor = android.R.color.holo_red_dark,
                        textColor = android.R.color.white,
                    ),
                )
            }.onFailure { error ->
                logger.warn("Unable to show Karoo in-ride alert", error)
            }

            runCatching {
                karooSystem?.beep(freq = 880, duration = 200, count = 1)
            }.onFailure { error ->
                logger.warn("Unable to play part alert beep", error)
            }
        }
    }
}

private fun KarooSystemService.beep(
    freq: Int,
    duration: Int,
    count: Int,
) {
    val beepList = mutableListOf(PlayBeepPattern.Tone(freq, duration))
    repeat(count - 1) {
        beepList.add(PlayBeepPattern.Tone(0, 50))
        beepList.add(PlayBeepPattern.Tone(freq, duration))
    }
    dispatch(PlayBeepPattern(beepList))
}

class NoOpPartAlertNotifier : PartAlertNotifier {
    override fun showAlert(
        bikeName: String,
        partName: String,
        alertText: String?,
        thresholdMeters: Int,
        currentMileageMeters: Int,
    ) {
        // Intentionally empty for tests or environments without Android UI.
    }
}
