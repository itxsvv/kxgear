package kxgear.bikeparts.ui.bikes

import kotlinx.coroutines.flow.StateFlow
import kxgear.bikeparts.domain.service.PartLifecycleGateway
import kxgear.bikeparts.ui.parts.PartDetailsUiState
import kxgear.bikeparts.ui.parts.PartDetailsViewModel

class BikeDetailsViewModel(
    partLifecycleGateway: PartLifecycleGateway,
) {
    private val partDetailsViewModel = PartDetailsViewModel(partLifecycleGateway)

    val uiState: StateFlow<PartDetailsUiState> = partDetailsViewModel.uiState

    suspend fun loadBike(bikeId: String) {
        partDetailsViewModel.loadBike(bikeId)
    }

    fun requestArchive(partId: String) {
        partDetailsViewModel.requestArchive(partId)
    }

    fun dismissArchive() {
        partDetailsViewModel.dismissArchive()
    }

    suspend fun confirmArchive() {
        partDetailsViewModel.confirmArchive()
    }

    fun requestDelete(partId: String) {
        partDetailsViewModel.requestDelete(partId)
    }

    fun dismissDelete() {
        partDetailsViewModel.dismissDelete()
    }

    suspend fun confirmDelete() {
        partDetailsViewModel.confirmDelete()
    }

    fun clearError() {
        partDetailsViewModel.clearError()
    }
}
