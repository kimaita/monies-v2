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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kimaita.monies.R
import com.kimaita.monies.models.export.ExportFormat
import com.kimaita.monies.models.export.ExportState
import com.kimaita.monies.services.TypeGroup
import java.time.LocalDate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator

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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DateBand.entries.forEach { b ->
                        BandChip(b.shortLabel(today), b.label, band == b) {
                            val (start, end) = b.range(today)
                            viewModel.setRange(start, end)
                        }
                    }
                    // Custom: a calendar icon, or the picked dates once a custom range is in force.
                    val custom = band == null
                    BandChip(
                        if (custom) rangeText(sel.startDate, sel.endDate, today) else null,
                        "Custom range", custom, icon = true,
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

/* ---------------- helpers ---------------- */

@Composable
private fun SectionLabel(t: String) =
    Text(
        t.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
fun ProgressPanel(s: ExportState.InProgress, onCancel: () -> Unit) {
    val fraction = s.total?.takeIf { it > 0 }?.let { (s.processed.toFloat() / it).coerceIn(0f, 1f) }
    val animated by animateFloatAsState(fraction ?: 0f, tween(300), label = "progress")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Exporting ${s.format.label}…", style = MaterialTheme.typography.titleSmall)
            Text(
                if (fraction != null) "${(animated * 100).toInt()}%"
                else "${s.processed}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (fraction != null) LinearProgressIndicator({ animated }, Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                s.total?.let { "${s.processed} of $it" }?.plus(" • runs in background")
                    ?: "Working… you can leave this screen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
