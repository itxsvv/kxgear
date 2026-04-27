package kxgear.bikeparts.ui.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kxgear.bikeparts.ui.common.KxgearTopBar
import kxgear.bikeparts.ui.common.formatMetersAsKilometers
import kxgear.bikeparts.ui.common.formatMetersAsKilometersValue
import kxgear.bikeparts.ui.common.parseKilometersInput

@Composable
fun BikeListScreen(
    state: BikeListUiState,
    canMutate: Boolean,
    onOpenBike: (String) -> Unit,
    onSelectBike: (String) -> Unit,
    onAddBike: (String, Int) -> Unit,
    onUpdateBike: (String, String, Int) -> Unit,
    onDeleteBike: (String) -> Unit,
) {
    var bikeDialog by remember { mutableStateOf<BikeEditDialogState?>(null) }
    var pendingDeleteBike by remember { mutableStateOf<BikeListItemUiModel?>(null) }
    val contentReady = state.bikes.isNotEmpty() || !state.isLoading

    Scaffold(
        topBar = {
            KxgearTopBar(title = "KXGear", canMutate = canMutate)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                state.bikes.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.bikes, key = { it.bikeId }) { bike ->
                            BikeRow(
                                bike = bike,
                                canMutate = canMutate,
                                onOpenBike = onOpenBike,
                                onSelectBike = onSelectBike,
                                onEditBike = {
                                    bikeDialog = BikeEditDialogState.Edit(bike.bikeId, bike.name, bike.mileageMeters)
                                },
                                onDeleteBike = { pendingDeleteBike = bike },
                            )
                        }
                    }
                }

                !state.isLoading -> {
                    EmptyBikeList(modifier = Modifier.weight(1f))
                }
            }
            if (contentReady) {
                Button(
                    onClick = { bikeDialog = BikeEditDialogState.Add },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canMutate,
                ) {
                    Text("Add Bike")
                }
            }
        }
    }

    bikeDialog?.let { dialogState ->
        BikeEditDialog(
            state = dialogState,
            canMutate = canMutate,
            onDismiss = { bikeDialog = null },
            onSave = { name, mileageMeters ->
                when (dialogState) {
                    BikeEditDialogState.Add -> onAddBike(name, mileageMeters)
                    is BikeEditDialogState.Edit -> onUpdateBike(dialogState.bikeId, name, mileageMeters)
                }
                bikeDialog = null
            },
        )
    }

    pendingDeleteBike?.let { bike ->
        DeleteBikeDialog(
            bikeName = bike.name,
            canMutate = canMutate,
            onDismiss = { pendingDeleteBike = null },
            onConfirm = {
                onDeleteBike(bike.bikeId)
                pendingDeleteBike = null
            },
        )
    }
}

@Composable
private fun EmptyBikeList(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No bikes",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Add a bike to start tracking parts.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BikeRow(
    bike: BikeListItemUiModel,
    canMutate: Boolean,
    onOpenBike: (String) -> Unit,
    onSelectBike: (String) -> Unit,
    onEditBike: () -> Unit,
    onDeleteBike: (String) -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (bike.isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bike.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatMetersAsKilometers(bike.mileageMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                BikeActionSlot(
                    label = "View",
                    onClick = { onOpenBike(bike.bikeId) },
                    enabled = true,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f),
                )
                BikeActionSlot(
                    label = "Edit",
                    onClick = onEditBike,
                    enabled = canMutate,
                    alignment = Alignment.Center,
                    modifier = Modifier.weight(1f),
                )
                BikeActionSlot(
                    label = "Delete",
                    onClick = { onDeleteBike(bike.bikeId) },
                    enabled = canMutate,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (bike.isActive) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Button(
                        onClick = { onSelectBike(bike.bikeId) },
                        enabled = canMutate,
                    ) {
                        Text("Activate")
                    }
                }
            }
        }
    }
}

@Composable
private fun BikeActionSlot(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    alignment: Alignment,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = alignment,
    ) {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private sealed interface BikeEditDialogState {
    data object Add : BikeEditDialogState

    data class Edit(
        val bikeId: String,
        val currentName: String,
        val currentMileageMeters: Int,
    ) : BikeEditDialogState
}

@Composable
private fun BikeEditDialog(
    state: BikeEditDialogState,
    canMutate: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var name by remember(state) {
        mutableStateOf(
            when (state) {
                BikeEditDialogState.Add -> ""
                is BikeEditDialogState.Edit -> state.currentName
            },
        )
    }
    var mileageInput by remember(state) {
        mutableStateOf(
            when (state) {
                BikeEditDialogState.Add -> ""
                is BikeEditDialogState.Edit -> formatMetersAsKilometersValue(state.currentMileageMeters)
            },
        )
    }
    var errorMessage by remember(state) { mutableStateOf<String?>(null) }
    val title =
        when (state) {
            BikeEditDialogState.Add -> "Add Bike"
            is BikeEditDialogState.Edit -> "Edit Bike"
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Bike name") },
                    singleLine = true,
                    enabled = canMutate,
                )
                OutlinedTextField(
                    value = mileageInput,
                    onValueChange = {
                        mileageInput = it
                        errorMessage = null
                    },
                    label = { Text("Bike mileage (km)") },
                    singleLine = true,
                    enabled = canMutate,
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onDismiss) {
                    Text("Back")
                }
                Button(
                    onClick = {
                        val mileageMeters =
                            parseKilometersInput(mileageInput) ?: run {
                                errorMessage = "Bike mileage must be a non-negative kilometer value with 0.1 km precision"
                                return@Button
                            }
                        onSave(name, mileageMeters)
                    },
                    enabled = canMutate && name.isNotBlank(),
                ) {
                    Text("Save")
                }
            }
        },
    )
}

@Composable
private fun DeleteBikeDialog(
    bikeName: String,
    canMutate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Bike") },
        text = { Text("Delete $bikeName?") },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onDismiss) {
                    Text("Back")
                }
                Button(onClick = onConfirm, enabled = canMutate) {
                    Text("Delete")
                }
            }
        },
    )
}
