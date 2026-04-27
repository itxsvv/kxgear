package kxgear.bikeparts.integration.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kxgear.bikeparts.R
import kxgear.bikeparts.integration.logging.BikePartsLogger
import kxgear.bikeparts.ui.common.formatMetersAsKilometers
import kotlin.math.absoluteValue

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
) : PartAlertNotifier {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Part maintenance alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Recurring bike part maintenance distance alerts"
                },
            )
        }
    }

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
        val notification =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(partName)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

        runCatching {
            notificationManager.notify(
                notificationId(partName = partName, thresholdMeters = thresholdMeters, currentMileageMeters = currentMileageMeters),
                notification,
            )
        }.onFailure { error ->
            logger.warn("Unable to show part alert notification", error)
        }
    }

    private fun notificationId(
        partName: String,
        thresholdMeters: Int,
        currentMileageMeters: Int,
    ): Int = "${partName}:${thresholdMeters}:${currentMileageMeters}".hashCode().absoluteValue

    companion object {
        private const val CHANNEL_ID = "part-maintenance-alerts"
    }
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
