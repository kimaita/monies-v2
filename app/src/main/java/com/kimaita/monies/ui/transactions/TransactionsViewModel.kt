package com.kimaita.monies.ui.transactions

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.getDatesForFilter
import com.kimaita.monies.models.isoDateTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionsRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(TransactionFilters())
    val filters = _filters.asStateFlow()


    val transactionTypes: StateFlow<Map<Int, TransactionType>> =
        repository.getTransactionTypes().map { list -> list.associateBy { it.id } }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    val categories: StateFlow<List<Category>> = repository.getCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val filteredTransactions: Flow<PagingData<Transaction>> = combine(
        _filters.map { it.searchQuery }.distinctUntilChanged().debounce(300L.milliseconds),
        _filters.map { it.copy(searchQuery = "") }.distinctUntilChanged()
    ) { query, filtersWithoutQuery ->
        filtersWithoutQuery.copy(searchQuery = query)
    }.flatMapLatest { filters ->
        val (startDate, endDate) = getDatesForFilter(filters.timeFilter)
        repository.getTransactions(
            startDate = startDate?.format(isoDateTimeFormatter),
            endDate = endDate?.format(isoDateTimeFormatter),
            transactionFilters = filters,
        )

    }.cachedIn(viewModelScope)


    fun updateFilters(updatedFilters: TransactionFilters) {
        _filters.value = updatedFilters
    }

    fun setSearchQuery(query: String) {
        _filters.value = _filters.value.copy(searchQuery = query)
    }

    fun setTimeFilter(timeFilter: TimeFilter) {
        _filters.value = _filters.value.copy(timeFilter = timeFilter)
    }

    fun setTypeFilter(typeId: Int?) {
        _filters.value = _filters.value.copy(typeFilter = typeId)
    }

    fun setCategoryFilter(category: Int?) {
        _filters.value = _filters.value.copy(categoryFilter = category)
    }

    fun setNatureFilter(isInc: Boolean?) {
        _filters.value = _filters.value.copy(natureFilter = isInc)
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _filters.value = _filters.value.copy(sortOrder = sortOrder)
    }

    fun clearFilters() {
        _filters.value = TransactionFilters()
    }

//    suspend fun exportToCsv(context: Context) {
//        withContext(Dispatchers.IO) {
//            val filters = _filters.value
//            val (startDate, endDate) = getDatesForFilter(filters.timeFilter)
//            val transactions = repository.getTransactionsForExport(
//                transactionFilters = filters,
//                startDate = startDate?.format(isoDateTimeFormatter),
//                endDate = endDate?.format(isoDateTimeFormatter)
//            )
//
//            if (transactions.isEmpty()) return@withContext
//
//            val csvHeader = "Code,Amount,Cost,Date,Time,Subject Name,Subject Number,Type\n"
//            val csvContent = transactions.joinToString("\n") { t ->
//                listOf(
//                    t.code,
//                    t.amount.toString(),
//                    t.cost?.toString() ?: "0.0",
//                    t.transactionTime?.toLocalDate()?.toString() ?: "",
//                    t.transactionTime?.toLocalTime()?.toString() ?: "",
//                    t.subject?.name ?: "",
//                    t.subject?.number ?: "",
//                    t.type.displayName ?: ""
//                ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
//            }
//
//            val fileName = "transactions_${System.currentTimeMillis()}.csv"
//            var contentUri: Uri? = null
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                val contentValues = ContentValues().apply {
//                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
//                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
//                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
//                }
//                val resolver = context.contentResolver
//                contentUri =
//                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
//                contentUri?.let { uri ->
//                    resolver.openOutputStream(uri)?.use { outputStream ->
//                        outputStream.write((csvHeader + csvContent).toByteArray())
//                    }
//                }
//            } else {
//                val downloadsDir =
//                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//                val file = File(downloadsDir, fileName)
//                file.writeText(csvHeader + csvContent)
//                contentUri = Uri.fromFile(file)
//            }
//
//            contentUri?.let { uri ->
//                val intent = Intent(Intent.ACTION_SEND).apply {
//                    type = "text/csv"
//                    putExtra(Intent.EXTRA_STREAM, uri)
//                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                }
//                val chooser = Intent.createChooser(intent, "Export Transactions").apply {
//                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                }
//                context.startActivity(chooser)
//            }
//        }
//    }
}
