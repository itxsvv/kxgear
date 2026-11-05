package kxgear.bikeparts.karoo

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.AppContainer
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import org.robolectric.shadows.ShadowLog

private const val LOG_TAG = "KXGear"

/** Seeds and reads the extension's own JSON store through the same classes the service uses. */
class KxgearHarness(private val karoo: FakeKarooRule) {
    val container = AppContainer(karoo.app, Dispatchers.IO)

    init {
        File(karoo.app.filesDir, "bikes").deleteRecursively()
        File(karoo.app.filesDir, "metadata").deleteRecursively()
        ShadowLog.reset()
    }

    /** Adds a local bike (active, being the first) with one installed part per name, alerting every [alertKm]. */
    fun seedBike(name: String, vararg parts: Pair<String, Int?>): String = runBlocking {
        container.bikeLifecycleService.addBike(name, 0)
        val bikeId = container.bikeLifecycleService.loadOverview().bikes.single { it.name == name }.bikeId
        parts.forEach { (partName, alertKm) ->
            val added = container.partLifecycleService.addPart(bikeId, partName, 0)
            val part = added.installedParts.single { it.name == partName }
            if (alertKm != null) {
                container.partLifecycleService.updatePart(bikeId, part.partId, partName, 0, alertKm, null)
            }
        }
        bikeId
    }

    fun bikeFile(bikeId: String): BikeFile = runBlocking { checkNotNull(container.bikeRepository.getBikeFile(bikeId)) }

    fun part(bikeId: String, name: String): Part = bikeFile(bikeId).parts.single { it.name == name }

    fun awaitPart(bikeId: String, name: String, timeoutMs: Long = 10_000, check: (Part) -> Boolean): Part =
        karoo.awaitValue(timeoutMs) { part(bikeId, name).takeIf(check) }

    /** The extension logs each controller transition and metric outcome; nothing else says when it is done. */
    fun awaitLog(contains: String, nth: Int = 1, timeoutMs: Long = 10_000): String =
        karoo.awaitValue(timeoutMs) {
            ShadowLog.getLogsForTag(LOG_TAG).filter { it.msg.contains(contains) }.getOrNull(nth - 1)?.msg
        }

    fun logCount(contains: String): Int = ShadowLog.getLogsForTag(LOG_TAG).count { it.msg.contains(contains) }

    /** Publishes [state] and waits for the controller to log [transition], which it does after its own state flips. */
    fun rideState(state: RideState, transition: String) {
        val before = logCount("Ride state changed from $transition")
        karoo.system.setRideState(state)
        awaitLog("Ride state changed from $transition", nth = before + 1)
    }

    fun awaitRideStateConsumer() {
        karoo.awaitValue { true.takeIf { RideState.Params in karoo.system.consumerParams } }
    }

    fun distance(meters: Double) =
        karoo.system.setDataPoint(DataPoint(DataType.Type.DISTANCE, mapOf(DataType.Field.SINGLE to meters)))
}
