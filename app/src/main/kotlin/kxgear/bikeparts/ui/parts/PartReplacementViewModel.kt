package kxgear.bikeparts.ui.parts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.service.PartLifecycleGateway
import kxgear.bikeparts.ui.common.parseRiddenMileageInput

data class PartReplacementUiState(
    val bikeId: String? = null,
    val oldPartId: String? = null,
    val oldPartName: String = "",
    val oldPartMileage: Int = 0,
    val riddenMileageInput: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class PartReplacementViewModel(
    private val partLifecycleGateway: PartLifecycleGateway,
) {
    private val _uiState = MutableStateFlow(PartReplacementUiState())
    val uiState: StateFlow<PartReplacementUiState> = _uiState.asStateFlow()

    suspend fun start(
        bikeId: String,
        oldPartId: String,
    ) {
        val part = partLifecycleGateway.getPart(bikeId, oldPartId) ?: throw RepositoryError.NotFound("Part not found: $oldPartId")
        _uiState.value =
            PartReplacementUiState(
                bikeId = bikeId,
                oldPartId = oldPartId,
                oldPartName = part.name,
                oldPartMileage = part.currentMileage,
                riddenMileageInput = "0.0",
            )
    }

    fun updateRiddenMileage(value: String) {
        _uiState.value = _uiState.value.copy(riddenMileageInput = value, errorMessage = null)
    }

    suspend fun submit(): Boolean {
        val state = _uiState.value
        val bikeId = state.bikeId ?: return false
        val oldPartId = state.oldPartId ?: return false
        val name = state.oldPartName

        val riddenMileage = parseRiddenMileage(state.riddenMileageInput) ?: return false

        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        return runCatching {
            partLifecycleGateway.replacePart(
                bikeId = bikeId,
                oldPartId = oldPartId,
                name = name,
                riddenMileage = riddenMileage,
            )
        }.fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(isSaving = false)
                true
            },
            onFailure = { error ->
                _uiState.value =
                    _uiState.value.copy(
                        isSaving = false,
                        errorMessage =
                            when (error) {
                                is RepositoryError -> error.message
                                else -> "Unable to replace part"
                            },
                    )
                false
            },
        )
    }

    private fun parseRiddenMileage(input: String): Int? {
        val parsedMileage = parseRiddenMileageInput(input)
        if (parsedMileage == null) {
            _uiState.value = _uiState.value.copy(riddenMileageInput = "0.0", errorMessage = null)
            return null
        }
        return parsedMileage
    }
}
