package com.kimaita.monies.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.kimaita.monies.ui.theme.MoniesTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DashboardPage() {
    Column(Modifier
        .fillMaxSize()
        .padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(12.dp))
        Eyebrow("THE FULL PICTURE")
        Spacer(Modifier.height(12.dp))
        OnbHeadline("See where it goes", fontSize = 27.sp)
        Spacer(Modifier.height(8.dp))
        OnbBody(
            "Highlights and reports — kept offline, exportable any way you like.",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            BreakdownCard()
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                StatCard(
                    Modifier.weight(1f),
                    "SPENT THIS MONTH",
                    "Ksh 31,204",
                    change = "▲ 12% vs last"
                )
                StatCard(Modifier.weight(1f), "TOP MERCHANT", "Naivas", sub = "Ksh 6,200")
            }
            CashFlowCard()
            ExportChips()
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun BreakdownCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .onbCard(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RankedPreviewRow("Groceries", 42, MaterialTheme.colorScheme.primary)
        RankedPreviewRow("Transport", 28, MoniesTheme.extra.outflow)
        RankedPreviewRow("Bills", 18, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun RankedPreviewRow(label: String, pct: Int, color: Color) {
    Column {
        Row {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                "$pct%",
                fontFamily = onbMono(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct / 42f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String? = null,
    change: String? = null
) {
    Column(modifier.onbCard()) {
        Text(
            label,
            fontFamily = onbMono(),
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            fontFamily = onbDisplay(),
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (change != null) {
            Spacer(Modifier.height(4.dp)); Text(
                change,
                fontFamily = onbMono(),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MoniesTheme.extra.negative
            )
        }
        if (sub != null) {
            Spacer(Modifier.height(4.dp)); Text(
                sub,
                fontFamily = onbMono(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CashFlowCard() {
    Column(Modifier
        .fillMaxWidth()
        .onbCard()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CASH FLOW · 6 WEEKS",
                fontFamily = onbMono(),
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowLegend(
                    MaterialTheme.colorScheme.primary,
                    "In"
                ); FlowLegend(MoniesTheme.extra.outflow, "Out")
            }
        }
        Spacer(Modifier.height(11.dp))
        MiniBars()
    }
}

@Composable
private fun FlowLegend(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier
            .size(9.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color))
        Text(label, fontFamily = onbMono(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniBars() {
    val data = listOf(
        0.60f to 0.40f,
        0.80f to 0.55f,
        0.45f to 0.70f,
        0.92f to 0.50f,
        0.55f to 0.62f,
        0.75f to 0.48f
    )
    val barShape =
        RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.forEach { (inF, outF) ->
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(inF)
                        .clip(barShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(outF)
                        .clip(barShape)
                        .background(MoniesTheme.extra.outflow)
                )
            }
        }
    }
}

@Composable
private fun ExportChips() {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        listOf("CSV", "Excel", "PDF").forEach {
            Text(
                it,
                fontFamily = onbMono(),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}
