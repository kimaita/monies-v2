package com.kimaita.monies.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.IconButton
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kimaita.monies.models.applyToAllOfSubject
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionBottomSheet(
    transaction: Transaction,
    onDismissRequest: () -> Unit,
    onSubjectClick: () -> Unit,
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
            onSubjectClick = onSubjectClick
        )
    }
}

private val sheetDateFmt = DateTimeFormatter.ofPattern("E MMM dd yyyy 'at' h:mma", Locale.ENGLISH)

@Composable
fun TransactionContent(
    transaction: Transaction,
    categories: List<Category>,
    onCategoryChange: (Category) -> Unit,
    onSubjectClick: () -> Unit,
) {
    val context = LocalContext.current


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = transaction.type.displayName ?: transaction.type.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            CurrencyText(transaction.amount) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.displayMedium.fontSize)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            transaction.transactionTime?.format(sheetDateFmt)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- Editable Category Section ---
        EditableCategoryChip(
            currentCategory = transaction.category?.name ?: "Assign Category",
            categories = categories,
            onCategorySelected = onCategoryChange
        )

        // --- Sender Detail Card ---
        Card(
            shape = RoundedCornerShape(16.dp),
            border = null,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSubjectClick() }) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(36.dp),
                        painter = painterResource(R.drawable.outline_account_circle_24),
                        contentDescription = null,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                val number = transaction.subject?.number

                // Name and Phone
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val name = transaction.subject?.name?.uppercase()

                    if (!name.isNullOrBlank()) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!number.isNullOrBlank()) {
                        if (!name.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!number.isNullOrBlank()) IconButton(onClick = {
                    copyToClipboard(context, "Phone", number)
                }) {
                    Icon(
                        painter = painterResource(R.drawable.outline_copy),
                        contentDescription = "Copy phone",
                    )
                }
            }
        }

        Text(
            text = transaction.message, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                icon = painterResource(R.drawable.outline_copy),
                label = "Message",
                onClick = {
                    copyToClipboard(context, "Message", transaction.message)
                })
            ActionButton(
                icon = painterResource(R.drawable.outline_copy),
                label = "Transaction ID",
                onClick = {
                    copyToClipboard(context, "Transaction ID", transaction.code)
                })
        }
    }
}

@Composable
fun EditableCategoryChip(
    currentCategory: String, categories: List<Category>, onCategorySelected: (Category) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,

                ) {
                Icon(
                    painter = painterResource(R.drawable.outline_category_search_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentCategory.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
fun ActionButton(
    icon: Painter, label: String, onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(
            painter = icon, contentDescription = null, modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label, style = MaterialTheme.typography.labelLarge
        )
    }
}

// ... inside TransactionSheet.kt

// --- Preview for testing ---
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
        subject = Subject(
            id = 101L, name = "John Doe", number = "0712345678"
        ),
        type = TransactionType(
            id = 1,
            name = "paybill",
            isInc = false,
            displayName = "Pay Bill",
        ),
        category = Category(
            id = 5, name = "Shopping"
        ),
    )

    AppTheme { // Wrapping in theme for accurate colors
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
