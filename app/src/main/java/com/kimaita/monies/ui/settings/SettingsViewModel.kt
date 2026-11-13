package com.kimaita.monies.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.AccountLine
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.UnparsedMessage
import com.kimaita.monies.models.CategoryApplyMode
import com.kimaita.monies.services.SmsSyncer
import com.kimaita.monies.ui.theme.ThemeMode
import com.kimaita.monies.worker.SmsSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TransactionsRepository,
    private val smsSyncer: SmsSyncer,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val accounts = repository.getAccountLines().state(emptyList<AccountLine>())
    val categories = repository.getCategories().state(emptyList<Category>())
    val applyMode = repository.categoryApplyMode.state(CategoryApplyMode.ASK)
    val hideBalances = repository.hideBalances.state(false)
    val theme = repository.themeMode.state(ThemeMode.SYSTEM)
    val unparsedCount = repository.getUnparsedCount().state(0)
    val unparsed = repository.getUnparsedMessages().state(emptyList<UnparsedMessage>())

    fun renameAccount(id: Int, label: String) {
        viewModelScope.launch { repository.renameAccount(id, label) }
    }

    fun mergeAccounts(source: Int, target: Int) {
        viewModelScope.launch { repository.mergeAccounts(source, target) }
    }

    fun addCategory(name: String, budget: Double?) {
        viewModelScope.launch { repository.addCategory(name, budget) }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { repository.updateCategory(category) }
    }

    fun deleteCategory(id: Int) {
        viewModelScope.launch { repository.deleteCategory(id) }
    }

    fun setApplyMode(mode: CategoryApplyMode) {
        viewModelScope.launch { repository.setCategoryApplyMode(mode) }
    }

    fun setHideBalances(hide: Boolean) {
        viewModelScope.launch { repository.setHideBalances(hide) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(UiModeManager::class.java)
                    .setApplicationNightMode(nightModeFor(mode))
            }
        }
    }

    fun reimport() {
        viewModelScope.launch {
            smsSyncer.exclusive { repository.clearForReimport() }
            SmsSyncWorker.enqueueImport(context)
        }
    }
}

/** The [UiModeManager] night mode that matches a theme choice. */
fun nightModeFor(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
}
