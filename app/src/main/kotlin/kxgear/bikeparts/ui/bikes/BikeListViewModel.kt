package kxgear.bikeparts.ui.bikes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.service.BikeLifecycleGateway

data class BikeListItemUiModel(
    val bikeId: String,
    val name: String,
    val mileageMeters: Int,
    val isActive: Boolean,
)

data class BikeListUiState(
    val isLoading: Boolean = false,
    val bikes: List<BikeListItemUiModel> = emptyList(),
    val errorMessage: String? = null,
)

class BikeListViewModel(
    private val bikeLifecycleGateway: BikeLifecycleGateway,
) {
    private val _uiState = MutableStateFlow(BikeListUiState(isLoading = true))
    val uiState: StateFlow<BikeListUiState> = _uiState.asStateFlow()

    suspend fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        runCatching {
            bikeLifecycleGateway.loadOverview()
        }.onSuccess { overview ->
            _uiState.value =
                BikeListUiState(
                    bikes =
                        overview.bikes.map { bike ->
                            BikeListItemUiModel(
                                bikeId = bike.bikeId,
                                name = bike.name,
                                mileageMeters = bike.mileageMeters,
                                isActive = bike.bikeId == overview.activeBikeId,
                            )
                        },
                )
        }.onFailure(::handleFailure)
    }

    suspend fun selectBike(bikeId: String) {
        runCatching { bikeLifecycleGateway.selectActiveBike(bikeId) }
            .onSuccess { refresh() }
            .onFailure(::handleFailure)
    }

    suspend fun addBike(
        name: String,
        mileageMeters: Int,
    ) {
        runCatching { bikeLifecycleGateway.addBike(name, mileageMeters) }
            .onSuccess { refresh() }
            .onFailure(::handleFailure)
    }

    suspend fun updateBike(
        bikeId: String,
        name: String,
        mileageMeters: Int,
    ) {
        runCatching { bikeLifecycleGateway.updateBike(bikeId, name, mileageMeters) }
            .onSuccess { refresh() }
            .onFailure(::handleFailure)
    }

    suspend fun deleteBike(bikeId: String) {
        runCatching { bikeLifecycleGateway.deleteBike(bikeId) }
            .onSuccess { refresh() }
            .onFailure(::handleFailure)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun handleFailure(error: Throwable) {
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                errorMessage =
                    when (error) {
                        is RepositoryError -> error.message
                        else -> "Unable to load bikes"
                    },
            )
    }
}
