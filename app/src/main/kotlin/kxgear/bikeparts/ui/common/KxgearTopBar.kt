package kxgear.bikeparts.ui.common

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KxgearTopBar(
    title: String,
    canMutate: Boolean,
    navigationIcon: @Composable () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = if (canMutate) title else "Disabled while riding",
                style = if (canMutate) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = navigationIcon,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = if (canMutate) Color.Black else Color.Red,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
    )
}
