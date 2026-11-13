package com.kimaita.monies.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R
import com.kimaita.monies.models.SortOrder

val SortOrder.label: String
    get() = when (this) {
        SortOrder.DATE_DESC -> "Newest first"
        SortOrder.DATE_ASC -> "Oldest first"
        SortOrder.AMOUNT_DESC -> "Largest first"
        SortOrder.AMOUNT_ASC -> "Smallest first"
    }

/** The sort icon button and its menu; a tick marks [current]. */
@Composable
fun SortMenu(current: SortOrder, onPick: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painter = painterResource(R.drawable.sort), contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    onClick = {
                        onPick(order)
                        expanded = false
                    },
                    trailingIcon = {
                        if (current == order) {
                            Icon(painterResource(R.drawable.check_24px), null, Modifier.size(18.dp))
                        }
                    },
                )
            }
        }
    }
}
