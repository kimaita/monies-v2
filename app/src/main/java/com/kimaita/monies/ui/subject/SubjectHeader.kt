package com.kimaita.monies.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R
import com.kimaita.monies.models.Category
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.ui.components.CurrencyText
import com.kimaita.monies.ui.theme.MoniesTheme

@Composable
fun ProfileHeader(isPerson: Boolean, name: String, number: String?, category: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (isPerson) R.drawable.outline_account_circle_24 else R.drawable.business_center_24dp),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            number?.let {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.label),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = category ?: "Category",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HighlightsSection(
    totalCost: Double,
    incomingTotal: Double?,
    outgoingTotal: Double?,
    incomingCount: Int,
    outgoingCount: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Transaction volume",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (incomingTotal != null && incomingCount > 0) {
                        HighlightStatItem(
                            modifier = Modifier.weight(1f),
                            amount = incomingTotal,
                            count = incomingCount,
                            label = "Incoming",
                            isInc = true
                        )
                    }

                    if (outgoingTotal != null && outgoingCount > 0) {
                        HighlightStatItem(
                            modifier = Modifier.weight(1f),
                            amount = outgoingTotal,
                            count = outgoingCount,
                            label = "Outgoing",
                            isInc = false
                        )
                    }
                }

                Text(
                    text = "Cost KES ${"%.2f".format(totalCost)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HighlightStatItem(
    modifier: Modifier = Modifier, amount: Double, count: Int, label: String, isInc: Boolean
) {
    val iconColor = if (isInc) MoniesTheme.extra.positive else MoniesTheme.extra.negative
    val icon = if (isInc) R.drawable.arrow_circle_down_24dp else R.drawable.arrow_circle_up_24dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(32.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            CurrencyText(amount = amount) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.titleMedium.fontSize)

                )
            }
            Text(
                text = "$count $label",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
