package kxgear.bikeparts.ui.parts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kxgear.bikeparts.ui.common.KxgearTopBar
import kxgear.bikeparts.ui.common.formatCreatedDate

@Composable
fun PartFormScreen(
    state: PartFormUiState,
    canMutate: Boolean,
    onNameChange: (String) -> Unit,
    onRiddenMileageChange: (String) -> Unit,
    onShowAlertDialog: () -> Unit,
    onDismissAlertDialog: () -> Unit,
    onAlertMileageChange: (String) -> Unit,
    onAlertTextChange: (String) -> Unit,
    onSaveAlert: () -> Unit,
    onRemoveAlert: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            KxgearTopBar(title = state.title, canMutate = canMutate)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.createdDate?.let { createdDate ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Added",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatCreatedDate(createdDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Part name") },
                    singleLine = true,
                    enabled = canMutate,
                )
            }
            OutlinedTextField(
                value = state.riddenMileageInput,
                onValueChange = onRiddenMileageChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ridden mileage (km)") },
                singleLine = true,
                enabled = canMutate,
            )
            if (state.isEditMode) {
                Button(
                    onClick = onShowAlertDialog,
                    enabled = canMutate,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(state.alertButtonLabel)
                }
            }
            state.errorMessage?.let { message ->
                Text(message)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onCancel) {
                    Text("Back")
                }
                Button(
                    onClick = onSave,
                    enabled = canMutate && !state.isSaving,
                ) {
                    Text(state.submitLabel)
                }
            }
        }
    }

    if (state.isEditMode && state.isAlertDialogVisible) {
        AlertDialog(
            onDismissRequest = onDismissAlertDialog,
            title = { Text("Alert") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.alertText,
                        onValueChange = onAlertTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Alert text") },
                        singleLine = true,
                        enabled = canMutate,
                    )
                    OutlinedTextField(
                        value = state.alertMileageInput,
                        onValueChange = onAlertMileageChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Alert mileage (km)") },
                        singleLine = true,
                        enabled = canMutate,
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(
                        onClick = onDismissAlertDialog,
                        modifier = Modifier.width(DialogButtonWidth),
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = onSaveAlert,
                        enabled = canMutate,
                        modifier = Modifier.width(DialogButtonWidth),
                    ) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = onRemoveAlert,
                        enabled = canMutate,
                    ) {
                        Text("Remove alert")
                    }
                }
            },
        )
    }
}

private val DialogButtonWidth = 96.dp
