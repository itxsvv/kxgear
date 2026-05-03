package kxgear.bikeparts.ui.parts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kxgear.bikeparts.ui.common.formatMetersAsKilometers

@Composable
fun PartListSection(
    title: String,
    parts: List<PartItemUiModel>,
    emptyMessage: String,
    canMutate: Boolean,
    onEditPart: (String) -> Unit,
    onDeletePart: ((String) -> Unit)? = null,
    onArchivePart: ((String) -> Unit)? = null,
    onReplacePart: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (parts.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            parts.forEach { part ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = part.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatMetersAsKilometers(part.riddenMileage),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PartActionSlot(
                                label = if (part.isArchived) null else "View",
                                onClick = if (part.isArchived) null else ({ onEditPart(part.partId) }),
                                enabled = true,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier.weight(1f),
                            )
                            PartActionSlot(
                                label = if (onReplacePart != null && !part.isArchived) "Replace" else null,
                                onClick = if (onReplacePart != null && !part.isArchived) ({ onReplacePart(part.partId) }) else null,
                                enabled = canMutate,
                                alignment = Alignment.Center,
                                modifier = Modifier.weight(1f),
                            )
                            PartActionSlot(
                                label = if (part.isArchived) "Delete" else "Archive",
                                onClick =
                                    when {
                                        part.isArchived && onDeletePart != null -> {
                                            { onDeletePart(part.partId) }
                                        }
                                        !part.isArchived && onArchivePart != null -> {
                                            { onArchivePart(part.partId) }
                                        }
                                        else -> null
                                    },
                                enabled = canMutate,
                                alignment = Alignment.CenterEnd,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartActionSlot(
    label: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    alignment: Alignment,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = alignment,
    ) {
        if (label != null && onClick != null) {
            Text(
                text = label,
                modifier =
                    Modifier
                        .clickable(enabled = enabled, onClick = onClick)
                        .padding(vertical = 2.dp),
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current.copy(alpha = DisabledActionAlpha)
                    },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private const val DisabledActionAlpha = 0.38f
