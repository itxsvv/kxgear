package kxgear.bikeparts.ui.parts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.service.BikeDetails
import kxgear.bikeparts.domain.service.PartLifecycleGateway

data class PartItemUiModel(
    val partId: String,
    val name: String,
    val riddenMileage: Int,
    val currentMileage: Int,
    val createdDate: Long,
    val isArchived: Boolean,
)

data class PendingArchivePart(
    val partId: String,
    val name: String,
)

data class PendingDeletePart(
    val partId: String,
    val name: String,
)

data class PartDetailsUiState(
    val bikeId: String? = null,
    val bikeName: String = "",
    val bikeMileageMeters: Int = 0,
    val isLoading: Boolean = false,
    val installedParts: List<PartItemUiModel> = emptyList(),
    val archivedParts: List<PartItemUiModel> = emptyList(),
    val lastAcceptedMetricValue: Int? = null,
    val lastAcceptedAt: Long? = null,
    val pendingArchivePart: PendingArchivePart? = null,
    val pendingDeletePart: PendingDeletePart? = null,
    val errorMessage: String? = null,
)

class PartDetailsViewModel(
    private val partLifecycleGateway: PartLifecycleGateway,
) {
    private val _uiState = MutableStateFlow(PartDetailsUiState(isLoading = true))
    val uiState: StateFlow<PartDetailsUiState> = _uiState.asStateFlow()

    suspend fun loadBike(bikeId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        runCatching { partLifecycleGateway.loadBikeDetails(bikeId) }
            .onSuccess(::applyDetails)
            .onFailure(::handleFailure)
    }

    fun requestArchive(partId: String) {
        val part =
            _uiState.value.installedParts.firstOrNull { it.partId == partId }
                ?: _uiState.value.archivedParts.firstOrNull { it.partId == partId }
                ?: return
        _uiState.value =
            _uiState.value.copy(
                pendingArchivePart = PendingArchivePart(partId = part.partId, name = part.name),
            )
    }

    fun dismissArchive() {
        _uiState.value = _uiState.value.copy(pendingArchivePart = null)
    }

    fun requestDelete(partId: String) {
        val part = _uiState.value.archivedParts.firstOrNull { it.partId == partId } ?: return
        _uiState.value =
            _uiState.value.copy(
                pendingDeletePart = PendingDeletePart(partId = part.partId, name = part.name),
            )
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(pendingDeletePart = null)
    }

    suspend fun confirmArchive() {
        val bikeId = _uiState.value.bikeId ?: return
        val partId = _uiState.value.pendingArchivePart?.partId ?: return
        runCatching { partLifecycleGateway.archivePart(bikeId, partId) }
            .onSuccess { details ->
                applyDetails(details)
                dismissArchive()
            }.onFailure(::handleFailure)
    }

    suspend fun confirmDelete() {
        val bikeId = _uiState.value.bikeId ?: return
        val partId = _uiState.value.pendingDeletePart?.partId ?: return
        runCatching { partLifecycleGateway.deletePart(bikeId, partId) }
            .onSuccess { details ->
                applyDetails(details)
                dismissDelete()
            }.onFailure(::handleFailure)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun applyDetails(details: BikeDetails) {
        _uiState.value =
            PartDetailsUiState(
                bikeId = details.bike.bikeId,
                bikeName = details.bike.name,
                bikeMileageMeters = details.bike.karooMileageMeters,
                installedParts = details.installedParts.map { it.toUiModel(isArchived = false) },
                archivedParts = details.archivedParts.map { it.toUiModel(isArchived = true) },
                lastAcceptedMetricValue = details.rideCursor.lastAcceptedMetricValue,
                lastAcceptedAt = details.rideCursor.lastAcceptedAt,
            )
    }

    private fun handleFailure(error: Throwable) {
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                errorMessage =
                    when (error) {
                        is RepositoryError -> error.message
                        else -> "Unable to load parts"
                    },
            )
    }
}

private fun kxgear.bikeparts.domain.model.Part.toUiModel(isArchived: Boolean): PartItemUiModel =
    PartItemUiModel(
        partId = partId,
        name = name,
        riddenMileage = riddenMileage,
        currentMileage = currentMileage,
        createdDate = createdDate,
        isArchived = isArchived,
    )
