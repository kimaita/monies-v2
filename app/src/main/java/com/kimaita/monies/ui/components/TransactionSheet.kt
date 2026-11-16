package com.kimaita.monies.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import com.kimaita.monies.ui.theme.MoniesTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kimaita.monies.models.applyToAllOfSubject
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R
import com.kimaita.monies.models.Category
import com.kimaita.monies.models.Subject
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionType
import com.kimaita.monies.ui.theme.AppTheme
import com.kimaita.monies.ui.util.copyToClipboard
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.kimaita.monies.ui.theme.TabularFigures


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionBottomSheet(
    transaction: Transaction,
    onDismissRequest: () -> Unit,
    onSubjectClick: () -> Unit,
    onTraceFrom: (() -> Unit)? = null,
    viewModel: TransactionSheetViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Category?>(null) }
    val subject = transaction.subject

    fun assign(category: Category, all: Boolean) =
        viewModel.setCategory(transaction.id, subject?.id, category.id, all)

    pending?.let { category ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Set category to ${category.name}") },
            text = { Text("Apply to this transaction only, or to everything from ${subject?.name ?: "this subject"}?") },
            confirmButton = {
                TextButton(onClick = { assign(category, true); pending = null }) {
                    Text("All from ${subject?.name ?: "this subject"}")
                }
            },
            dismissButton = {
                TextButton(onClick = { assign(category, false); pending = null }) {
                    Text("Only this transaction")
                }
            },
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        TransactionContent(
            transaction = transaction,
            categories = categories.map { Category(it.id, it.name) },
            onCategoryChange = { picked ->
                val all = applyToAllOfSubject(mode, subject != null)
                if (all == null) pending = picked else assign(picked, all)
            },
            onSubjectClick = onSubjectClick,
            onTraceFrom = onTraceFrom,
        )
    }
}

private val sheetDateFmt = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

@Composable
fun TransactionContent(
    transaction: Transaction,
    categories: List<Category>,
    onCategoryChange: (Category) -> Unit,
    onSubjectClick: () -> Unit,
    onTraceFrom: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val subject = transaction.subject
    val typeLabel = transaction.type.displayName ?: transaction.type.name
    val subjectName = subject?.name?.takeIf { it.isNotBlank() }?.toTitleCase()
    val initial = (subjectName ?: typeLabel).firstOrNull()?.uppercase() ?: "?"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(initial)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(typeLabel, style = MaterialTheme.typography.titleSmall)
                transaction.transactionTime?.format(sheetDateFmt)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
        }

        CurrencyText(transaction.amount, isInc = transaction.isInc) { text ->
            Text(
                text,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                style = MaterialTheme.typography.displaySmall,
                color = if (transaction.isInc) MoniesTheme.extra.positive else MoniesTheme.extra.negative,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.displaySmall.fontSize)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DetailRow("Category") {
            CategoryButton(transaction.category?.name, categories, onCategoryChange)
        }
        val cost = transaction.cost
        if (cost != null && cost > 0) {
            DetailRow("Fee") { MoneyValue(cost) }
            DetailRow("Total") { MoneyValue(transaction.total) }
        }
        DetailRow("Code") {
            Text(transaction.code, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularFigures))
            CopyIcon("Copy transaction code") { copyToClipboard(context, "Transaction ID", transaction.code) }
        }

        if (subject != null) {
            val number = subject.number?.takeIf { it.isNotBlank() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSubjectClick)
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialAvatar(initial)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(subjectName ?: number ?: typeLabel, style = MaterialTheme.typography.titleSmall)
                    if (subjectName != null && number != null) {
                        Text(number, style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                }
                if (number != null && '*' !in number) {
                    CopyIcon("Copy phone") { copyToClipboard(context, "Phone", number) }
                }
                Icon(
                    painter = painterResource(R.drawable.keyboard_arrow_right),
                    contentDescription = "Open ${subjectName ?: "subject"}",
                    tint = muted
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        SmsCard(transaction.message) { copyToClipboard(context, "Message", transaction.message) }

        if (onTraceFrom != null && transaction.transactionTime != null) {
            OutlinedButton(
                onClick = onTraceFrom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text("Trace since this") }
        }
    }
}

@Composable
private fun InitialAvatar(initial: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        value()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MoneyValue(amount: Double) {
    CurrencyText(amount) { Text(it, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularFigures)) }
}

@Composable
private fun CopyIcon(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            painter = painterResource(R.drawable.outline_copy),
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryButton(
    current: String?, categories: List<Category>, onCategorySelected: (Category) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = if (current == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, if (current == null) accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(current ?: "Add category", style = MaterialTheme.typography.labelLarge, color = accent)
            Icon(
                painter = painterResource(R.drawable.keyboard_arrow_right),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(90f),
                tint = accent
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, onClick = {
                    onCategorySelected(category)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun SmsCard(message: String, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(start = 14.dp, end = 4.dp, top = 12.dp)
        ) {
            Text(
                message,
                modifier = Modifier.padding(end = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expanded) "Show less" else "Show full message",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                CopyIcon("Copy message", onCopy)
            }
        }
    }
}

private fun String.toTitleCase() =
    lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ENGLISH) } }

@Preview(showBackground = true)
@Composable
fun PreviewSheetContent() {
    val mockData = Transaction(
        id = 1L,
        message = "RSL876GHJ1 Confirmed. Ksh2,300.00 sent to JOHN DOE 0712345678 on 12/12/25 at 4:21 PM. New M-PESA balance is Ksh12,450.00. Transaction cost, Ksh12.00.",
        amount = 2300.0,
        cost = 12.0,
        code = "RSL876GHJ1",
        transactionTime = LocalDateTime.now(),
        isInc = false,
        subject = Subject(id = 101L, name = "JOHN DOE", number = "0712345678"),
        type = TransactionType(id = 1, name = "send", isInc = false, displayName = "Send Money"),
        category = Category(id = 5, name = "Shopping"),
    )

    AppTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 16.dp)
        ) {
            TransactionContent(
                transaction = mockData,
                categories = listOf(Category(5, "Shopping")),
                onCategoryChange = {},
                onSubjectClick = {})
        }
    }
}
