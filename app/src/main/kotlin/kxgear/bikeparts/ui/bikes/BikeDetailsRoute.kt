package kxgear.bikeparts.ui.bikes

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kxgear.bikeparts.domain.service.PartLifecycleGateway
import kxgear.bikeparts.ui.parts.PartDetailsScreen
import kxgear.bikeparts.ui.parts.PartFormScreen
import kxgear.bikeparts.ui.parts.PartFormViewModel
import kxgear.bikeparts.ui.parts.PartReplacementScreen
import kxgear.bikeparts.ui.parts.PartReplacementViewModel

@Composable
fun BikeDetailsRoute(
    bikeId: String,
    bikeDetailsViewModel: BikeDetailsViewModel,
    partLifecycleGateway: PartLifecycleGateway,
    canMutate: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val partFormViewModel = remember { PartFormViewModel(partLifecycleGateway) }
    val partReplacementViewModel = remember { PartReplacementViewModel(partLifecycleGateway) }
    var screen by remember { mutableStateOf<Screen>(Screen.Details) }

    BackHandler {
        when (screen) {
            Screen.Details -> onBack()
            Screen.Form -> screen = Screen.Details
            is Screen.Replacement -> screen = Screen.Details
        }
    }

    when (val activeScreen = screen) {
        Screen.Details -> {
            val state by bikeDetailsViewModel.uiState.collectAsState()
            PartDetailsScreen(
                state = state,
                canMutate = canMutate,
                onBack = onBack,
                onAddPart = {
                    partFormViewModel.startCreate(bikeId)
                    screen = Screen.Form
                },
                onEditPart = { partId ->
                    scope.launch {
                        partFormViewModel.startEdit(bikeId, partId)
                        screen = Screen.Form
                    }
                },
                onRequestReplacePart = { partId ->
                    scope.launch {
                        partReplacementViewModel.start(bikeId, partId)
                        screen = Screen.Replacement(partId)
                    }
                },
                onRequestArchivePart = bikeDetailsViewModel::requestArchive,
                onRequestDeletePart = bikeDetailsViewModel::requestDelete,
                onDismissArchive = bikeDetailsViewModel::dismissArchive,
                onConfirmArchive = {
                    scope.launch {
                        bikeDetailsViewModel.confirmArchive()
                    }
                },
                onDismissDelete = bikeDetailsViewModel::dismissDelete,
                onConfirmDelete = {
                    scope.launch {
                        bikeDetailsViewModel.confirmDelete()
                    }
                },
            )
        }

        Screen.Form -> {
            val state by partFormViewModel.uiState.collectAsState()
            PartFormScreen(
                state = state,
                canMutate = canMutate,
                onNameChange = partFormViewModel::updateName,
                onRiddenMileageChange = partFormViewModel::updateRiddenMileage,
                onShowAlertDialog = partFormViewModel::showAlertDialog,
                onDismissAlertDialog = partFormViewModel::dismissAlertDialog,
                onAlertMileageChange = partFormViewModel::updateAlertMileage,
                onAlertTextChange = partFormViewModel::updateAlertText,
                onSaveAlert = {
                    scope.launch {
                        if (partFormViewModel.saveAlertConfig()) {
                            bikeDetailsViewModel.loadBike(bikeId)
                        }
                    }
                },
                onRemoveAlert = {
                    scope.launch {
                        if (partFormViewModel.removeAlert()) {
                            bikeDetailsViewModel.loadBike(bikeId)
                        }
                    }
                },
                onSave = {
                    scope.launch {
                        if (partFormViewModel.submit()) {
                            bikeDetailsViewModel.loadBike(bikeId)
                            screen = Screen.Details
                        }
                    }
                },
                onCancel = {
                    screen = Screen.Details
                },
            )
        }

        is Screen.Replacement -> {
            val state by partReplacementViewModel.uiState.collectAsState()
            PartReplacementScreen(
                state = state,
                canMutate = canMutate,
                onRiddenMileageChange = partReplacementViewModel::updateRiddenMileage,
                onSave = {
                    scope.launch {
                        if (partReplacementViewModel.submit()) {
                            bikeDetailsViewModel.loadBike(bikeId)
                            screen = Screen.Details
                        }
                    }
                },
                onCancel = {
                    screen = Screen.Details
                },
            )
        }
    }
}

private sealed interface Screen {
    data object Details : Screen

    data object Form : Screen

    data class Replacement(
        val partId: String,
    ) : Screen
}
