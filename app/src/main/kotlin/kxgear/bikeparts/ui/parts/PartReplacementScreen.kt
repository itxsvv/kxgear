package kxgear.bikeparts.ui.parts

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kxgear.bikeparts.ui.common.KxgearTopBar
import kxgear.bikeparts.ui.common.formatMetersAsKilometers

@Composable
fun PartReplacementScreen(
    state: PartReplacementUiState,
    canMutate: Boolean,
    onRiddenMileageChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            KxgearTopBar(title = "Replace Part", canMutate = canMutate)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Replacing ${state.oldPartName}")
            Text("Current mileage: ${formatMetersAsKilometers(state.oldPartMileage)}")
            OutlinedTextField(
                value = state.riddenMileageInput,
                onValueChange = onRiddenMileageChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ridden mileage (km)") },
                singleLine = true,
                enabled = canMutate,
            )
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
                    Text("Save")
                }
            }
        }
    }
}
