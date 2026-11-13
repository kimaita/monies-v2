package com.kimaita.monies.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.models.CategoryApplyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionSheetViewModel @Inject constructor(
    private val repository: TransactionsRepository,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mode: StateFlow<CategoryApplyMode> = repository.categoryApplyMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryApplyMode.ASK)

    fun setCategory(transactionId: Long, subjectId: Long?, categoryId: Int, allFromSubject: Boolean) {
        viewModelScope.launch {
            repository.assignCategory(transactionId, subjectId, categoryId, allFromSubject)
        }
    }
}
