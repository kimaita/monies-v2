package com.kimaita.monies.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kimaita.monies.R
import com.kimaita.monies.data.dao.dtos.AccountLine
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.models.CategoryApplyMode
import com.kimaita.monies.ui.components.TextDialog
import com.kimaita.monies.ui.navigateSingleTop
import com.kimaita.monies.models.ThemeMode
import com.kimaita.monies.ui.util.formatKes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val applyMode by viewModel.applyMode.collectAsStateWithLifecycle()
    val hide by viewModel.hideBalances.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val unparsedCount by viewModel.unparsedCount.collectAsStateWithLifecycle()

    var renaming by remember { mutableStateOf<AccountLine?>(null) }
    var mergeSource by remember { mutableStateOf<AccountLine?>(null) }
    var mergePair by remember { mutableStateOf<Pair<AccountLine, AccountLine>?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var addingCategory by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var confirmReimport by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.outline_arrow_back_24), "Back")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Header("Accounts") }
            items(accounts, key = { "a${it.accountId}" }) { a ->
                val canMerge = accounts.any { it.provider == a.provider && it.accountId != a.accountId }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${a.count} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (a.dormant) Text(
                            "Inactive since ${LocalDateTime.parse(a.lastActivity).format(dateFmt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { renaming = a }) { Text("Rename") }
                    if (canMerge) TextButton(onClick = { mergeSource = a }) { Text("Merge") }
                }
            }

            item { Header("Categories") }
            items(categories, key = { "c${it.id}" }) { c ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        c.monthlyBudget?.let {
                            Text(
                                "Budget ${formatKes(it)} a month",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { editingCategory = c }) { Text("Edit") }
                    TextButton(onClick = { deletingCategory = c }) { Text("Delete") }
                }
            }
            item {
                TextButton(onClick = { addingCategory = true }, Modifier.padding(horizontal = 8.dp)) {
                    Text("Add category")
                }
            }

            item { Header("When categorising") }
            items(CategoryApplyMode.entries) { m ->
                RadioRow(
                    when (m) {
                        CategoryApplyMode.ASK -> "Ask every time"
                        CategoryApplyMode.ONE -> "Only that transaction"
                        CategoryApplyMode.ALL -> "All from the same person or business"
                    },
                    selected = applyMode == m,
                ) { viewModel.setApplyMode(m) }
            }

            item { Header("Privacy") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Hide balances", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = hide, onCheckedChange = viewModel::setHideBalances)
                }
            }

            item { Header("Messages") }
            item {
                Text(
                    "Unrecognised messages ($unparsedCount)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigateSingleTop("settings/unrecognised") }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            item {
                Text(
                    "Re-import messages",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmReimport = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            item { Header("Theme") }
            items(ThemeMode.entries) { m ->
                RadioRow(
                    when (m) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    },
                    selected = theme == m,
                ) { viewModel.setTheme(m) }
            }
        }
    }

    renaming?.let { a ->
        TextDialog(
            title = "Rename account", initial = a.name, label = "Name",
            onDismiss = { renaming = null },
            onConfirm = { viewModel.renameAccount(a.accountId, it); renaming = null },
        )
    }

    mergeSource?.let { source ->
        AlertDialog(
            onDismissRequest = { mergeSource = null },
            title = { Text("Merge ${source.name} into") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Latest on ${source.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Samples(viewModel, source.accountId)
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    accounts.filter { it.provider == source.provider && it.accountId != source.accountId }
                        .forEach { t ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { mergePair = source to t; mergeSource = null }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(t.name, style = MaterialTheme.typography.bodyLarge)
                                Samples(viewModel, t.accountId)
                            }
                        }
                }
            },
            confirmButton = { TextButton(onClick = { mergeSource = null }) { Text("Cancel") } },
        )
    }

    mergePair?.let { (source, target) ->
        AlertDialog(
            onDismissRequest = { mergePair = null },
            title = { Text("Merge accounts?") },
            text = {
                Text(
                    "All ${source.count} transactions of ${source.name} move to ${target.name}, " +
                        "and ${source.name} is removed. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.mergeAccounts(source.accountId, target.accountId); mergePair = null }) {
                    Text("Merge")
                }
            },
            dismissButton = { TextButton(onClick = { mergePair = null }) { Text("Cancel") } },
        )
    }

    if (addingCategory) CategoryDialog(
        initial = null,
        onDismiss = { addingCategory = false },
        onConfirm = { name, budget -> viewModel.addCategory(name, budget); addingCategory = false },
    )
    editingCategory?.let { c ->
        CategoryDialog(
            initial = c,
            onDismiss = { editingCategory = null },
            onConfirm = { name, budget ->
                viewModel.updateCategory(c.copy(name = name, monthlyBudget = budget)); editingCategory = null
            },
        )
    }
    deletingCategory?.let { c ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete ${c.name}?") },
            text = { Text("Transactions and people using it become uncategorised.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(c.id); deletingCategory = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text("Cancel") } },
        )
    }

    if (confirmReimport) AlertDialog(
        onDismissRequest = { confirmReimport = false },
        title = { Text("Re-import messages?") },
        text = {
            Text(
                "All transactions and unrecognised messages are deleted and read again from your inbox. " +
                    "Accounts, people, names and categories are kept; categories set on single transactions are lost."
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.reimport(); confirmReimport = false; navController.navigateUp() }) {
                Text("Re-import")
            }
        },
        dismissButton = { TextButton(onClick = { confirmReimport = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Header(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
)
