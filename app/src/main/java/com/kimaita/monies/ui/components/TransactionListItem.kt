package com.kimaita.monies.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.dp
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.ui.util.formatAmount
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class TransactionItemStyle {
    SINGLE_LINE, TWO_LINE
}

private val shortDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

fun formatRelativeDate(dateTime: LocalDateTime?): String {
    if (dateTime == null) return ""
    val today = LocalDate.now()
    val date = dateTime.toLocalDate()
    return when {
        date.isEqual(today) -> "Today"
        date.isEqual(today.minusDays(1)) -> "Yesterday"
        else -> date.format(shortDateFmt)
    }
}

@Composable
fun TransactionListItem(
    modifier: Modifier = Modifier,
    transaction: Transaction,
    style: TransactionItemStyle = TransactionItemStyle.SINGLE_LINE,
    /** Shows the balance the SMS gave after this transaction, under the date. */
    showBalance: Boolean = false,
    /** Offered in the sheet as "Trace since this" when set. */
    onTraceFrom: ((Transaction) -> Unit)? = null,
    onSubjectClick: (Long) -> Unit,
) {
    val subject = transaction.subject
    val transactionType = transaction.type
    val typeLabel = transactionType.displayName ?: transactionType.name
    var showSheet by remember { mutableStateOf(false) }

    if (showSheet) {
        TransactionBottomSheet(
            transaction = transaction,
            onDismissRequest = { showSheet = false },
            onSubjectClick = {
                showSheet = false
                subject?.id?.let { onSubjectClick(it) }
            },
            onTraceFrom = onTraceFrom?.let { trace ->
                {
                    showSheet = false
                    trace(transaction)
                }
            })
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showSheet = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (style == TransactionItemStyle.TWO_LINE) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(vertical = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .then(
                        if (subject != null) Modifier.clickable { onSubjectClick(subject.id) }
                        else Modifier
                    ), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = subject?.name?.firstOrNull()?.uppercase()
                        ?: typeLabel.firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            modifier = if (style == TransactionItemStyle.SINGLE_LINE) Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
            else Modifier.weight(1f)
        ) {
            if (style == TransactionItemStyle.TWO_LINE) {
                Text(
                    text = (subject?.name ?: subject?.number)?.uppercase() ?: typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = Ellipsis
                )
            }
            CurrencyText(
                amount = transaction.amount,
                isInc = transaction.isInc,
            ) { text ->
                Text(
                    text = text,
                    style = if (style == TransactionItemStyle.TWO_LINE) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatRelativeDate(transaction.transactionTime),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val balance = transaction.balance
            if (showBalance && balance != null) {
                Text(
                    text = "Bal ${formatAmount(balance)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

