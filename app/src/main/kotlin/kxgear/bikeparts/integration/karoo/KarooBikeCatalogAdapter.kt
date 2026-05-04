package kxgear.bikeparts.integration.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.Bikes
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kxgear.bikeparts.domain.service.KarooBikeCatalogGateway
import kxgear.bikeparts.domain.service.KarooBikeSnapshot
import kxgear.bikeparts.integration.logging.BikePartsLogger
import kotlin.math.roundToInt

class KarooBikeCatalogAdapter(
    private val karooSystem: KarooSystemService,
    private val logger: BikePartsLogger,
) : KarooBikeCatalogGateway {
    override suspend fun listBikes(): List<KarooBikeSnapshot> =
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            var consumerId: String? = null

            fun resumeOnce(bikes: List<KarooBikeSnapshot>) {
                if (finished.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(bikes)
                }
            }

            fun cleanupConsumer() {
                consumerId?.let(karooSystem::removeConsumer)
                consumerId = null
            }

            fun subscribe() {
                consumerId =
                    karooSystem.addConsumer<Bikes>(
                        params = Bikes.Params,
                        onError = { message ->
                            cleanupConsumer()
                            logger.warn("Karoo bike list error: $message")
                            resumeOnce(emptyList())
                        },
                        onComplete = {
                            cleanupConsumer()
                            resumeOnce(emptyList())
                        },
                    ) { event ->
                        cleanupConsumer()
                        resumeOnce(
                            event.bikes.map { bike ->
                                KarooBikeSnapshot(
                                    karooBikeId = bike.id,
                                    name = bike.name,
                                    mileageMeters = bike.odometer.roundToInt(),
                                )
                            },
                        )
                    }
            }

            continuation.invokeOnCancellation {
                cleanupConsumer()
            }

            if (karooSystem.connected) {
                subscribe()
            } else {
                karooSystem.connect { connected ->
                    if (!connected) {
                        logger.warn("Karoo system unavailable for startup bike sync")
                        resumeOnce(emptyList())
                        return@connect
                    }
                    subscribe()
                }
            }
        }
}
