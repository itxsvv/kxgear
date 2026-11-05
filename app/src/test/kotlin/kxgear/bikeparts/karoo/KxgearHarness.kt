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
import kxgear.bikeparts.domain.model.SharedMetadata
import kxgear.bikeparts.extension.BikePartsExtension
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
        parts.forEach { (partName, alertKm) -> addPart(bikeId, partName, alertKm = alertKm) }
        bikeId
    }

    /** Adds an installed part; [alertKm] sets the alert threshold in whole kilometers. */
    fun addPart(bikeId: String, name: String, alertKm: Int? = null, alertText: String? = null): Part = runBlocking {
        val added = container.partLifecycleService.addPart(bikeId, name, 0)
        val part = added.installedParts.single { it.name == name }
        if (alertKm != null) {
            container.partLifecycleService.updatePart(bikeId, part.partId, name, 0, alertKm, alertText)
        }
        part
    }

    /** Changes a part's alert threshold, which resets its alert counter. */
    fun setAlert(bikeId: String, name: String, alertKm: Int, alertText: String? = null) {
        val part = part(bikeId, name)
        runBlocking {
            container.partLifecycleService.updatePart(bikeId, part.partId, part.name, part.riddenMileage, alertKm, alertText)
        }
    }

    fun archivePart(bikeId: String, name: String) {
        val part = part(bikeId, name)
        runBlocking { container.partLifecycleService.archivePart(bikeId, part.partId) }
    }

    fun selectBike(bikeId: String) {
        runBlocking { container.bikeLifecycleService.selectActiveBike(bikeId) }
    }

    fun metadata(): SharedMetadata = runBlocking { container.metadataRepository.read() }

    fun saveBikeFile(bikeFile: BikeFile) {
        runBlocking { container.bikeRepository.saveBikeFile(bikeFile) }
    }

    fun deleteBikeFile(bikeId: String) {
        runBlocking { container.bikeRepository.deleteBikeFile(bikeId) }
    }

    fun allBikeFiles(): List<BikeFile> = runBlocking { container.bikeRepository.listBikeFiles() }

    /** Registers the extension and waits for the streams it consumes. */
    fun startService() {
        karoo.host<BikePartsExtension>()
        karoo.awaitStreamConsumer(DataType.Type.DISTANCE)
        awaitRideStateConsumer()
    }

    fun consumerIds(): Set<String> = karoo.system.consumerIds.toSet()

    /** Ids registered after [before]; the fake exposes ids and params only as separate lists. */
    fun newConsumerIds(before: Collection<String>): List<String> = karoo.system.consumerIds.filterNot { it in before }

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
