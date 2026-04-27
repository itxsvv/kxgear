package kxgear.bikeparts.extension

import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kxgear.bikeparts.AppContainer
import kxgear.bikeparts.integration.karoo.RideUpdateController

class BikePartsExtension : KarooExtension("kxgear", "0.1.0") {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContainer: AppContainer
    private lateinit var rideUpdateController: RideUpdateController

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(applicationContext, Dispatchers.IO)
        rideUpdateController = appContainer.createRideUpdateController(serviceScope)
        rideUpdateController.start()
    }

    override fun onDestroy() {
        if (::rideUpdateController.isInitialized) {
            rideUpdateController.stop()
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
