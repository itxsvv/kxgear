package kxgear.bikeparts.ui.parts

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kxgear.bikeparts.ui.common.KxgearTopBar
import kxgear.bikeparts.ui.common.formatMetersAsKilometers

@Composable
fun PartDetailsScreen(
    state: PartDetailsUiState,
    canMutate: Boolean,
    onBack: () -> Unit,
    onAddPart: () -> Unit,
    onEditPart: (String) -> Unit,
    onRequestReplacePart: (String) -> Unit,
    onRequestArchivePart: (String) -> Unit,
    onRequestDeletePart: (String) -> Unit,
    onDismissArchive: () -> Unit,
    onConfirmArchive: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Scaffold(
        topBar = {
            KxgearTopBar(
                title = if (state.bikeName.isBlank()) "Bike Details" else state.bikeName,
                canMutate = canMutate,
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = Color.White)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Bike mileage: ${formatMetersAsKilometers(state.bikeMileageMeters)}")
            Button(
                onClick = onAddPart,
                modifier = Modifier.fillMaxWidth(),
                enabled = canMutate,
            ) {
                Text("Add Part")
            }
            state.errorMessage?.let { message ->
                Text(message)
            }
            PartListSection(
                title = "Installed Parts",
                parts = state.installedParts,
                emptyMessage = "No installed parts yet.",
                canMutate = canMutate,
                onEditPart = onEditPart,
                onReplacePart = onRequestReplacePart,
                onArchivePart = onRequestArchivePart,
            )
            PartListSection(
                title = "Archived Parts",
                parts = state.archivedParts,
                emptyMessage = "No archived parts yet.",
                canMutate = canMutate,
                onEditPart = onEditPart,
                onDeletePart = onRequestDeletePart,
            )
        }
    }

    state.pendingArchivePart?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissArchive,
            title = { Text("Archive part?") },
            text = { Text("Archive ${pending.name} and stop future ride updates for it?") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(
                        onClick = onDismissArchive,
                        modifier = Modifier.width(DialogButtonWidth),
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = onConfirmArchive,
                        enabled = canMutate,
                        modifier = Modifier.width(DialogButtonWidth),
                    ) {
                        Text("Archive")
                    }
                }
            },
        )
    }

    state.pendingDeletePart?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete part?") },
            text = { Text("Delete archived part ${pending.name}? This cannot be undone.") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(
                        onClick = onDismissDelete,
                        modifier = Modifier.width(DialogButtonWidth),
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = onConfirmDelete,
                        enabled = canMutate,
                        modifier = Modifier.width(DialogButtonWidth),
                    ) {
                        Text("Delete")
                    }
                }
            },
        )
    }
}

private val DialogButtonWidth = 96.dp
