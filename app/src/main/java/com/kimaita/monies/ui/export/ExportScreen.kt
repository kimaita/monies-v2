package com.kimaita.monies.ui.export

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kimaita.monies.R
import com.kimaita.monies.models.export.ExportFormat
import com.kimaita.monies.models.export.ExportState
import com.kimaita.monies.services.TypeGroup
import com.kimaita.monies.services.isSelected
import com.kimaita.monies.ui.export.components.ProgressPanel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun List<Int>.toggle(id: Int): List<Int> = if (id in this) this - id else this + id
private val FORMAT_ORDER =
    listOf(ExportFormat.XLSX, ExportFormat.CSV, ExportFormat.PDF, ExportFormat.JSON)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val sel by viewModel.sel.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val state by viewModel.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val accounts = options?.accounts.orEmpty()
    val band = sel.band(today)

    LaunchedEffect(Unit) { if (viewModel.applyHandoff()) filtersExpanded = true }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "action"
                ) { s ->
                    when (s) {
                        is ExportState.InProgress -> ProgressPanel(s, onCancel = viewModel::cancel)
                        else -> Box(Modifier.padding(16.dp)) {
                            Button(
                                onClick = viewModel::export,
                                enabled = (count ?: 0) > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 54.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Export${count?.let { " ${"%,d".format(it)}" } ?: ""}",
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "· ${sel.format.ext.uppercase()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // header + live scope line
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Export",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    scopeLine(
                        count, sel, options?.types.orEmpty(),
                        accounts.firstOrNull { it.id == sel.accountId }?.name, today
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel("Format")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                FORMAT_ORDER.forEach { f ->
                    FormatCell(
                        f, selected = sel.format == f, onClick = { viewModel.setFormat(f) },
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 128.dp)
                    )
                }
            }

            if (accounts.size > 1) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Account")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirChip("All", sel.accountId == null) { viewModel.setAccount(null) }
                    accounts.forEach { a ->
                        DirChip(a.name, sel.accountId == a.id) { viewModel.setAccount(a.id) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Date range")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateBand.entries.forEach { b ->
                        DirChip(b.label, band == b) {
                            val (start, end) = b.range(today)
                            viewModel.setRange(start, end)
                        }
                    }
                    DirChip(
                        if (band == null) rangeText(sel.startDate, sel.endDate, today) else "Custom…",
                        band == null
                    ) { showRangePicker = true }
                }
            }

            if (!filtersExpanded) {
                AddFiltersRow(sel.activeFilterCount(options?.types.orEmpty()), onClick = { filtersExpanded = true })
            } else {
                FilterPanel(viewModel, onCollapse = { filtersExpanded = false })
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showRangePicker) DateRangeDialog(
        sel.startDate, sel.endDate, today,
        onDismiss = { showRangePicker = false },
    ) { start, end -> viewModel.setRange(start, end); showRangePicker = false }

    when (val s = state) {
        is ExportState.Completed -> AlertDialog(
            onDismissRequest = viewModel::acknowledge,
            icon = { Icon(painterResource(R.drawable.check_circle), null) },
            title = { Text("Export complete") },
            text = {
                Text(buildString {
                    if (s.count >= 0) append("${"%,d".format(s.count)} transactions saved · ")
                    append(s.fileName)
                })
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { shareFile(context, s.uri, s.mime); viewModel.acknowledge() }) {
                        Icon(painterResource(R.drawable.share), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Share")
                    }
                    Button(onClick = { openFile(context, s.uri, s.mime); viewModel.acknowledge() }) {
                        Icon(painterResource(R.drawable.open_in_new), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Open")
                    }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::acknowledge) { Text("Not now") } },
        )

        is ExportState.Failed -> AlertDialog(
            onDismissRequest = viewModel::acknowledge,
            icon = { Icon(painterResource(R.drawable.error), null) },
            title = { Text("Export failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = viewModel::acknowledge) { Text("OK") } },
        )

        else -> Unit
    }
}

/* ---------------- format ---------------- */

@Composable
private fun FormatCell(
    format: ExportFormat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) cs.primaryContainer else cs.surface)
            .border(
                1.5.dp,
                if (selected) cs.primary else cs.outlineVariant,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                format.label,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                ".${format.ext}", style = MaterialTheme.typography.labelSmall,
                color = if (selected) cs.onPrimaryContainer.copy(alpha = 0.7f) else cs.onSurfaceVariant
            )
        }
        if (selected) Icon(
            painterResource(R.drawable.check_circle),
            null,
            Modifier.size(20.dp),
            tint = cs.primary
        )
    }
}

/* ---------------- filters ---------------- */

@Composable
private fun AddFiltersRow(activeCount: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(cs.primaryContainer),
                Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.add_24),
                    null,
                    Modifier.size(18.dp),
                    tint = cs.onPrimaryContainer
                )
            }
            Column {
                Text(
                    if (activeCount > 0) "Filters · $activeCount" else "Add filters",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Subject, type, category, direction",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
        Icon(painterResource(R.drawable.keyboard_arrow_right), null, tint = cs.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(vm: ExportViewModel, onCollapse: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sel by vm.sel.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val query by vm.subjectQuery.collectAsStateWithLifecycle()
    val results by vm.subjectResults.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::clearFilters) { Text("Clear all") }
                IconButton(onClick = onCollapse) {
                    Icon(
                        painterResource(R.drawable.collapse),
                        "Collapse"
                    )
                }
            }
        }

        FilterLabel("Direction")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirChip("All", sel.nature == null) { vm.setNature(null) }
            DirChip("Money in", sel.nature == true) { vm.setNature(true) }
            DirChip("Money out", sel.nature == false) { vm.setNature(false) }
        }

        if (sel.search.isNotBlank()) {
            FilterLabel("Search")
            InputChip(
                selected = true,
                onClick = { vm.setSearch("") },
                label = { Text(sel.search, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    Icon(painterResource(R.drawable.close_small_24dp), null, Modifier.size(16.dp))
                })
        }

        FilterLabel("Subject")
        OutlinedTextField(
            value = query, onValueChange = vm::setSubjectQuery, singleLine = true,
            placeholder = { Text("Search subjects…") },
            leadingIcon = { Icon(painterResource(R.drawable.search), null) },
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions.Default,
        )
        if (sel.subjects.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sel.subjects.forEach { ref ->
                InputChip(
                    selected = true,
                    onClick = { vm.removeSubject(ref) },
                    label = { Text(ref.name) },
                    trailingIcon = {
                        Icon(
                            painterResource(R.drawable.close_small_24dp),
                            null,
                            Modifier.size(16.dp)
                        )
                    })
            }
        }
        if (query.isNotBlank() && results.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                8.dp
            )
        ) {
            results.filter { r -> sel.subjects.none { it.id == r.id } }.take(10).forEach { ref ->
                AssistChip(
                    onClick = { vm.addSubject(ref) }, label = { Text(ref.name) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.add_24),
                            null,
                            Modifier.size(16.dp)
                        )
                    })
            }
        }

        options?.categories?.takeIf { it.isNotEmpty() }?.let { cats ->
            FilterLabel("Categories")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    FilterChip(
                        selected = c.id in sel.categoryIds,
                        onClick = { vm.toggleCategory(c.id) },
                        label = { Text(c.name) })
                }
            }
        }

        options?.types?.takeIf { it.isNotEmpty() }?.let { types ->
            FilterLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t ->
                    FilterChip(
                        selected = t.isSelected(sel.typeIds),
                        onClick = { vm.toggleType(t) },
                        label = { Text(t.label) })
                }
            }
        }
    }
}

@Composable
private fun DirChip(label: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })

/* ---------------- date picker ---------------- */

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    start: LocalDate?,
    end: LocalDate?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate, LocalDate) -> Unit,
) {
    val lastDay = today.toUtcMillis()
    val st = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.toUtcMillis(),
        initialSelectedEndDateMillis = if (start != null) end?.toUtcMillis() else null,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= lastDay
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = st.selectedStartDateMillis != null,
                onClick = {
                    st.selectedStartDateMillis?.let {
                        val from = it.toUtcDate()
                        onPick(from, st.selectedEndDateMillis?.toUtcDate() ?: from)
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DateRangePicker(st, showModeToggle = true) }
}

/* ---------------- helpers ---------------- */

@Composable
private fun SectionLabel(t: String) =
    Text(
        t.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

@Composable
private fun FilterLabel(t: String) =
    Text(
        t.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )

private fun scopeLine(
    count: Int?,
    sel: ExportSelections,
    typeGroups: List<TypeGroup>,
    accountName: String?,
    today: LocalDate,
): String {
    val n = count?.let { "%,d".format(it) } ?: "…"
    val filters = sel.activeFilterCount(typeGroups)
    return buildString {
        append("$n transactions")
        accountName?.let { append(" · $it") }
        append(" · ${sel.dateLabel(today)}")
        if (filters > 0) append(" · $filters filter${if (filters == 1) "" else "s"}")
    }
}

private fun openFile(context: Context, uri: Uri, mime: String) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        shareFile(context, uri, mime)
    }
}
fun shareFile(context: Context, uri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, "Share export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}