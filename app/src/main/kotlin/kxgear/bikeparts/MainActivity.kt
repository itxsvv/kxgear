package kxgear.bikeparts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kxgear.bikeparts.integration.karoo.RideRecordingState
import kxgear.bikeparts.integration.karoo.RideStateStore
import kxgear.bikeparts.ui.bikes.BikeDetailsViewModel
import kxgear.bikeparts.ui.bikes.BikeDetailsRoute
import kxgear.bikeparts.ui.bikes.BikeListScreen
import kxgear.bikeparts.ui.bikes.BikeListViewModel
import kxgear.bikeparts.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(this)

        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current
                val bikeListViewModel = remember { BikeListViewModel(appContainer.bikeLifecycleService) }
                val rideState by RideStateStore.state.collectAsState()
                val canMutate = rideState == RideRecordingState.IDLE
                var screen by remember { mutableStateOf<Screen>(Screen.BikeList) }

                fun returnToBikeList() {
                    scope.launch {
                        bikeListViewModel.refresh()
                        screen = Screen.BikeList
                    }
                }

                LaunchedEffect(screen) {
                    if (screen == Screen.BikeList) {
                        bikeListViewModel.refresh()
                    }
                }

                DisposableEffect(lifecycleOwner, screen) {
                    val observer =
                        LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                scope.launch {
                                    when (val activeScreen = screen) {
                                        Screen.BikeList -> bikeListViewModel.refresh()
                                        is Screen.BikeDetails -> activeScreen.bikeDetailsViewModel.loadBike(activeScreen.bikeId)
                                    }
                                }
                            }
                        }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                BackHandler(enabled = screen != Screen.BikeList) {
                    returnToBikeList()
                }

                when (val activeScreen = screen) {
                    Screen.BikeList -> {
                        val state by bikeListViewModel.uiState.collectAsState()
                        BikeListScreen(
                            state = state,
                            canMutate = canMutate,
                            onOpenBike = { bikeId ->
                                scope.launch {
                                    val bikeDetailsViewModel = BikeDetailsViewModel(appContainer.partLifecycleService)
                                    bikeDetailsViewModel.loadBike(bikeId)
                                    screen = Screen.BikeDetails(bikeId, bikeDetailsViewModel)
                                }
                            },
                            onSelectBike = { bikeId ->
                                scope.launch {
                                    bikeListViewModel.selectBike(bikeId)
                                }
                            },
                            onAddBike = { name, mileageMeters ->
                                scope.launch {
                                    bikeListViewModel.addBike(name, mileageMeters)
                                }
                            },
                            onUpdateBike = { bikeId, name, mileageMeters ->
                                scope.launch {
                                    bikeListViewModel.updateBike(bikeId, name, mileageMeters)
                                }
                            },
                            onDeleteBike = { bikeId ->
                                scope.launch {
                                    bikeListViewModel.deleteBike(bikeId)
                                }
                            },
                        )
                    }

                    is Screen.BikeDetails -> {
                        BikeDetailsRoute(
                            bikeId = activeScreen.bikeId,
                            bikeDetailsViewModel = activeScreen.bikeDetailsViewModel,
                            partLifecycleGateway = appContainer.partLifecycleService,
                            canMutate = canMutate,
                            onBack = {
                                returnToBikeList()
                            },
                        )
                    }
                }
            }
        }
    }

    private sealed interface Screen {
        data object BikeList : Screen

        data class BikeDetails(
            val bikeId: String,
            val bikeDetailsViewModel: BikeDetailsViewModel,
        ) : Screen
    }
}
