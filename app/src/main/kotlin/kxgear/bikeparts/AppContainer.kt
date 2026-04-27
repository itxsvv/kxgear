package kxgear.bikeparts

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kxgear.bikeparts.data.repository.JsonBikeRepository
import kxgear.bikeparts.data.repository.JsonMetadataRepository
import kxgear.bikeparts.data.storage.AtomicJsonFileStore
import kxgear.bikeparts.domain.repository.BikeRepository
import kxgear.bikeparts.domain.repository.MetadataRepository
import kxgear.bikeparts.domain.service.BikeLifecycleService
import kxgear.bikeparts.domain.service.BikePartsService
import kxgear.bikeparts.domain.service.PartLifecycleService
import kxgear.bikeparts.domain.service.RideProcessingService
import kxgear.bikeparts.integration.karoo.KarooRideAdapter
import kxgear.bikeparts.integration.karoo.RideUpdateController
import kxgear.bikeparts.integration.logging.BikePartsLogger
import kxgear.bikeparts.integration.notifications.AndroidPartAlertNotifier
import java.nio.file.Path

class AppContainer(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val filesRoot: Path = context.filesDir.toPath()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val fileStore = AtomicJsonFileStore()

    val logger = BikePartsLogger()

    val bikeRepository: BikeRepository =
        JsonBikeRepository(
            bikesDirectory = filesRoot.resolve("bikes"),
            json = json,
            fileStore = fileStore,
            dispatcher = dispatcher,
        )

    val metadataRepository: MetadataRepository =
        JsonMetadataRepository(
            metadataPath = filesRoot.resolve("metadata").resolve("shared-metadata.json"),
            json = json,
            fileStore = fileStore,
            dispatcher = dispatcher,
        )

    val bikePartsService = BikePartsService()
    private val partAlertNotifier = AndroidPartAlertNotifier(context, logger)
    val bikeLifecycleService =
        BikeLifecycleService(
            bikeRepository = bikeRepository,
            metadataRepository = metadataRepository,
            logger = logger,
        )
    val partLifecycleService =
        PartLifecycleService(
            bikeRepository = bikeRepository,
            bikePartsService = bikePartsService,
            logger = logger,
        )
    val rideProcessingService =
        RideProcessingService(
            metadataRepository = metadataRepository,
            bikeRepository = bikeRepository,
            bikePartsService = bikePartsService,
            partAlertNotifier = partAlertNotifier,
            logger = logger,
        )
    val karooRideAdapter = KarooRideAdapter(KarooSystemService(context), logger)

    fun createRideUpdateController(scope: CoroutineScope): RideUpdateController =
        RideUpdateController(
            adapter = karooRideAdapter,
            rideProcessingService = rideProcessingService,
            logger = logger,
            scope = scope,
        )

}
