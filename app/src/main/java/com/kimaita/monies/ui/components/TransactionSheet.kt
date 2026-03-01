package com.kimaita.monies.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionBottomSheet(
    transaction: Transaction,
    onDismissRequest: () -> Unit,
    onCategoryChange: (String) -> Unit,
    onSubjectClick: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        TransactionContent(
            transaction = transaction,
            onCategoryChange = onCategoryChange,
            onSubjectClick = onSubjectClick
        )
    }
}

@Composable
fun TransactionContent(
    transaction: Transaction, onCategoryChange: (String) -> Unit, onSubjectClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern(
            "E MMM dd yyyy 'at' h:mma",
            Locale.ENGLISH
        )
    }


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
                text = transaction.type.displayName.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            CurrencyText(transaction.amount) { text ->
                Text(
                    text, style = MaterialTheme.typography.displayMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            transaction.transactionTime?.format(dateFormatter)?.let {
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

                // Name and Phone
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val name = transaction.subject?.name?.uppercase()
                    val number = transaction.subject?.number

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

//                IconButton(onClick = {
//                    TODO("Implement copy to clipboard")
//                    clipboardManager.setClipEntry(AnnotatedString(transaction.senderPhone))
//                    Toast.makeText(context, "Phone copied", Toast.LENGTH_SHORT).show()
//                }) {
//                    Icon(
//                        painter = painterResource(R.drawable.outline_copy),
//                        contentDescription = "Copy Phone",
//                    )
//                }
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
                    clipboardManager.setText(AnnotatedString(transaction.message))
                    android.widget.Toast.makeText(
                        context,
                        "Message copied",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                })
            ActionButton(
                icon = painterResource(R.drawable.outline_copy),
                label = "Transaction ID",
                onClick = {
                    clipboardManager.setText(AnnotatedString(transaction.code))
                    android.widget.Toast.makeText(
                        context,
                        "Transaction ID copied",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                })
        }
    }
}

@Composable
fun EditableCategoryChip(
    currentCategory: String, onCategorySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = listOf("Allowance", "Shopping", "Bills", "Entertainment", "Transport")

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
                DropdownMenuItem(text = { Text(category) }, onClick = {
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
            TransactionContent(transaction = mockData, onCategoryChange = {}, onSubjectClick = {})
        }
    }
}
