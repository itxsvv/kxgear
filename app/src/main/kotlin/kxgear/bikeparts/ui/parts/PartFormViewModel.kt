package kxgear.bikeparts.ui.parts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.service.PartLifecycleGateway
import kxgear.bikeparts.ui.common.formatMetersAsKilometersValue
import kxgear.bikeparts.ui.common.parseRiddenMileageInput

private enum class PartFormMode {
    CREATE,
    EDIT,
}

data class PartFormUiState(
    val bikeId: String? = null,
    val partId: String? = null,
    val name: String = "",
    val riddenMileageInput: String = "",
    val createdDate: Long? = null,
    val targetAlertMileageInput: String = "",
    val curAlertMileage: Int = 0,
    val targetAlertMileage: Int = 0,
    val alertText: String = "",
    val isAlertDialogVisible: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val title: String = "Add Part",
    val submitLabel: String = "Save",
) {
    val isEditMode: Boolean
        get() = partId != null

    val alertButtonLabel: String
        get() =
            if (targetAlertMileage <= 0) {
                "Add Alert"
            } else {
                "Alert ${formatMetersAsKilometersValue(curAlertMileage)} / ${formatMetersAsKilometersValue(targetAlertMileage).removeSuffix(".0")} km"
            }
}

class PartFormViewModel(
    private val partLifecycleGateway: PartLifecycleGateway,
) {
    private var mode = PartFormMode.CREATE
    private val _uiState = MutableStateFlow(PartFormUiState())
    val uiState: StateFlow<PartFormUiState> = _uiState.asStateFlow()

    fun startCreate(bikeId: String) {
        mode = PartFormMode.CREATE
        _uiState.value =
            PartFormUiState(
                bikeId = bikeId,
                title = "Add Part",
                submitLabel = "Save",
            )
    }

    suspend fun startEdit(
        bikeId: String,
        partId: String,
    ) {
        mode = PartFormMode.EDIT
        val part = partLifecycleGateway.getPart(bikeId, partId) ?: throw RepositoryError.NotFound("Part not found: $partId")
        _uiState.value =
            PartFormUiState(
                bikeId = bikeId,
                partId = part.partId,
                name = part.name,
                riddenMileageInput = formatMetersAsKilometersValue(part.riddenMileage),
                createdDate = part.createdDate,
                targetAlertMileageInput = part.targetAlertMileage.takeIf { it > 0 }?.div(1000)?.toString().orEmpty(),
                curAlertMileage = part.curAlertMileage,
                targetAlertMileage = part.targetAlertMileage,
                alertText = part.alertText.orEmpty(),
                title = "Edit Part",
                submitLabel = "Save",
            )
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun updateRiddenMileage(value: String) {
        _uiState.value = _uiState.value.copy(riddenMileageInput = value, errorMessage = null)
    }

    fun showAlertDialog() {
        if (!_uiState.value.isEditMode) {
            return
        }
        _uiState.value = _uiState.value.copy(isAlertDialogVisible = true, errorMessage = null)
    }

    fun dismissAlertDialog() {
        _uiState.value = _uiState.value.copy(isAlertDialogVisible = false, errorMessage = null)
    }

    fun updateTargetAlertMileage(value: String) {
        _uiState.value = _uiState.value.copy(targetAlertMileageInput = value, errorMessage = null)
    }

    fun updateAlertText(value: String) {
        _uiState.value = _uiState.value.copy(alertText = value, errorMessage = null)
    }

    suspend fun saveAlertConfig(): Boolean {
        val targetAlertMileageInput = _uiState.value.targetAlertMileageInput.trim()
        if (targetAlertMileageInput.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Target alert mileage is required")
            return false
        }
        val targetAlertMileage = targetAlertMileageInput.toIntOrNull()
        if (targetAlertMileage == null || targetAlertMileage <= 0) {
            _uiState.value =
                _uiState.value.copy(
                    errorMessage = "Target alert mileage must be a positive whole number in kilometers",
                )
            return false
        }
        _uiState.value =
            _uiState.value.copy(
                targetAlertMileageInput = targetAlertMileage.toString(),
                errorMessage = null,
            )
        return persistEditPart(
            targetAlertMileage = targetAlertMileage,
            alertText = _uiState.value.alertText.trim().ifBlank { null },
            persistedCurAlertMileage = 0,
            persistedTargetAlertMileage = targetAlertMileage * 1000,
        )
    }

    suspend fun removeAlert(): Boolean {
        _uiState.value = _uiState.value.copy(targetAlertMileageInput = "", alertText = "", errorMessage = null)
        return persistEditPart(
            targetAlertMileage = null,
            alertText = null,
            persistedCurAlertMileage = 0,
            persistedTargetAlertMileage = 0,
        )
    }

    suspend fun submit(): Boolean {
        val bikeId = _uiState.value.bikeId ?: return false
        val name = _uiState.value.name.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Part name is required")
            return false
        }

        val parsedRiddenMileage = parseRiddenMileageInput(_uiState.value.riddenMileageInput)
        if (parsedRiddenMileage == null) {
            _uiState.value = _uiState.value.copy(riddenMileageInput = "0.0", errorMessage = null)
            return false
        }
        val riddenMileage = parsedRiddenMileage

        if (mode == PartFormMode.CREATE) {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            return runCatching {
                partLifecycleGateway.addPart(bikeId, name, riddenMileage)
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
                                    else -> "Unable to save part"
                                },
                        )
                    false
                },
            )
        }

        return persistEditPart(
            targetAlertMileage = _uiState.value.targetAlertMileageInput.trim().toIntOrNull(),
            alertText = _uiState.value.alertText.trim().ifBlank { null },
            riddenMileage = riddenMileage,
            name = name,
            closeAlertDialog = false,
        )
    }

    private suspend fun persistEditPart(
        targetAlertMileage: Int?,
        alertText: String?,
        riddenMileage: Int? = null,
        name: String? = null,
        closeAlertDialog: Boolean = true,
        persistedCurAlertMileage: Int? = null,
        persistedTargetAlertMileage: Int? = null,
    ): Boolean {
        if (mode != PartFormMode.EDIT) {
            return false
        }
        val bikeId = _uiState.value.bikeId ?: return false
        val partId = _uiState.value.partId ?: return false
        val normalizedName = (name ?: _uiState.value.name).trim()
        if (normalizedName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Part name is required")
            return false
        }
        val normalizedRiddenMileage =
            riddenMileage ?: parseRiddenMileageInput(_uiState.value.riddenMileageInput)?.also { }
            ?: run {
                _uiState.value = _uiState.value.copy(riddenMileageInput = "0.0", errorMessage = null)
                return false
            }

        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        return runCatching {
            partLifecycleGateway.updatePart(
                bikeId = bikeId,
                partId = partId,
                name = normalizedName,
                riddenMileage = normalizedRiddenMileage,
                targetAlertMileage = targetAlertMileage,
                alertText = alertText,
            )
        }.fold(
            onSuccess = {
                _uiState.value =
                    _uiState.value.copy(
                        isSaving = false,
                        curAlertMileage = persistedCurAlertMileage ?: _uiState.value.curAlertMileage,
                        targetAlertMileage = persistedTargetAlertMileage ?: _uiState.value.targetAlertMileage,
                        isAlertDialogVisible = if (closeAlertDialog) false else _uiState.value.isAlertDialogVisible,
                        errorMessage = null,
                    )
                true
            },
            onFailure = { error ->
                _uiState.value =
                    _uiState.value.copy(
                        isSaving = false,
                        errorMessage =
                            when (error) {
                                is RepositoryError -> error.message
                                else -> "Unable to save part"
                            },
                    )
                false
            },
        )
    }

}
