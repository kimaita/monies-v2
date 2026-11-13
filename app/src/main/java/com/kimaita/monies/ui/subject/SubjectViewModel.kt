package com.kimaita.monies.ui.subject

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.database.toDomainModel
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.Subject
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import com.kimaita.monies.models.export.NamedRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SubjectViewModel @Inject constructor(
    private val repository: TransactionsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val subjectId: Long? = savedStateHandle.get<String>("subjectId")?.toLongOrNull()
    private val _filters = MutableStateFlow(TransactionFilters(subjectFilter = subjectId))
    val filters = _filters.asStateFlow()

    val subjectDetails: StateFlow<Subject?> = flow {
        if (subjectId != null) {
            repository.getSubjects(subjectId).collect { subjects ->
                emit(subjects.firstOrNull()?.toDomainModel())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val subjectStats: StateFlow<SubjectStats> = _filters.flatMapLatest {
        if (subjectId != null) {
            repository.getSubjectStats(
                subjectId = subjectId,
                startDate = null,
                endDate = null
            )
        } else {
            flowOf(SubjectStats())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubjectStats())

    @OptIn(ExperimentalCoroutinesApi::class)
    val subjectTransactions: Flow<PagingData<Transaction>> =
        _filters.flatMapLatest { filters ->
            repository.getTransactions(
                startDate = null,
                endDate = null,
                transactionFilters = filters,
            )

        }.cachedIn(viewModelScope)


    /** The subject's category name, or null when it has none. */
    val categoryName: StateFlow<String?> = flow {
        if (subjectId != null) {
            emitAll(
                combine(repository.getSubjects(subjectId), repository.getCategories()) { subjects, categories ->
                    val id = subjects.firstOrNull()?.categoryId
                    categories.firstOrNull { it.id == id }?.name
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _subjectQuery = MutableStateFlow("")
    val subjectQuery: StateFlow<String> = _subjectQuery.asStateFlow()
    fun setSubjectQuery(q: String) {
        _subjectQuery.value = q
    }

    /** Merge targets: the existing subject search, minus this subject. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val subjectResults: StateFlow<List<NamedRef<Long>>> = _subjectQuery
        .debounce(250.milliseconds).distinctUntilChanged()
        .mapLatest { q ->
            (if (q.isBlank()) repository.topSubjects() else repository.searchSubjects(q.trim()))
                .filter { it.id != subjectId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun rename(name: String) {
        subjectId?.let { viewModelScope.launch { repository.renameSubject(it, name) } }
    }

    /** Merges this subject into [target]; [onDone] runs after a successful merge. */
    fun mergeInto(target: NamedRef<Long>, onDone: () -> Unit) {
        val source = subjectId ?: return
        viewModelScope.launch { if (repository.mergeSubject(source, target.id)) onDone() }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _filters.value = _filters.value.copy(sortOrder = sortOrder)

    }
}

