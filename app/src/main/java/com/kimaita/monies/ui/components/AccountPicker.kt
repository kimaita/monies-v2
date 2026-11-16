package com.kimaita.monies.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.R
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.models.export.NamedRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The app-wide account choice, for the picker in each tab's app bar. */
@HiltViewModel
class AccountPickerViewModel @Inject constructor(
    private val repository: TransactionsRepository,
) : ViewModel() {
    data class State(val accounts: List<NamedRef<Int>>, val selected: Int?)

    val state: StateFlow<State?> =
        combine(repository.getAccountBalances(), repository.selectedAccount) { lines, selected ->
            State(lines.map { NamedRef(it.accountId, it.name) }, selected)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(id: Int?) {
        viewModelScope.launch { repository.setSelectedAccount(id) }
    }
}

/** A chip naming the selected account, with a menu to change it. Hidden with fewer than two accounts. */
@Composable
fun AccountPicker(vm: AccountPickerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    if (s.accounts.size < 2) return
    var open by remember { mutableStateOf(false) }
    val current = s.accounts.firstOrNull { it.id == s.selected }
    Box {
        AssistChip(
            onClick = { open = true },
            label = {
                Text(
                    current?.name ?: "All accounts",
                    Modifier.widthIn(max = 120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.keyboard_arrow_right), null,
                    Modifier
                        .size(18.dp)
                        .rotate(90f),
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<NamedRef<Int>?>(null) + s.accounts).forEach { ref ->
                val selected = ref?.id == current?.id
                DropdownMenuItem(
                    text = { Text(ref?.name ?: "All accounts") },
                    onClick = { open = false; if (!selected) vm.select(ref?.id) },
                    trailingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.check_24px), "Selected") }
                    } else null,
                )
            }
        }
    }
}
