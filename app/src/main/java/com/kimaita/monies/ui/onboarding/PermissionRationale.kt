package com.kimaita.monies.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R

data class PermissionRationale(
    val icon: Int,
    val title: String,
    val description: String,
    val isRequired: Boolean,
)

@Composable
fun permissionRationales(): List<PermissionRationale> = remember {
    listOf(
        PermissionRationale(
            R.drawable.ic_sms, "Read & receive SMS",
            "To retrieve your M-PESA messages for processing.", isRequired = true,
        ),
        PermissionRationale(
            R.drawable.ic_phone,
            "Phone number & carrier",
            "To match each line to the right account. Recommended if you have more than one number and want to separate transactions.",
            isRequired = false
        ),
        PermissionRationale(
            R.drawable.ic_notifications, "Notifications",
            "To show progress for longer running tasks.", isRequired = false,
        ),
    )
}

@Composable
fun PermissionRow(item: PermissionRationale, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(item.icon), null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                PermissionMarker(item.isRequired)
            }
            Text(
                item.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionMarker(required: Boolean) {
    val label = if (required) "Required" else "Optional"
    val container: Color
    val content: Color
    if (required) {
        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        content = MaterialTheme.colorScheme.primary
    } else {
        container = MaterialTheme.colorScheme.surfaceContainerHighest
        content = MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
