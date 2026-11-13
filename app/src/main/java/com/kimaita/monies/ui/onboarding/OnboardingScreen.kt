package com.kimaita.monies.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = 4
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pageCount - 1

    Scaffold(modifier = modifier, containerColor = OnbColors.Paper) { padding ->
        Column(Modifier
            .fillMaxSize()
            .padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(!isLast, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = onSkip) {
                        Text(
                            "Skip",
                            color = OnbColors.Muted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                when (page) {
                    0 -> HeroPage(active = pagerState.currentPage == 0, parallax = offset)
                    1 -> AutoPage(active = pagerState.currentPage == 1)
                    2 -> DashboardPage()
                    else -> PermissionsPage(active = pagerState.currentPage == 3)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .padding(top = 6.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PagerIndicator(pageCount, pagerState.currentPage)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (isLast) onFinish() else scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnbColors.Emerald,
                        contentColor = Color.White
                    ),
                ) {
                    Text(
                        if (isLast) "Get started" else "Next",
                        fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PagerIndicator(pageCount: Int, currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(pageCount) { i ->
            val selected = i == currentPage
            val w by animateDpAsState(if (selected) 28.dp else 8.dp, label = "w")
            val c by animateColorAsState(
                if (selected) OnbColors.Coral else OnbColors.DotIdle,
                label = "c"
            )
            Box(Modifier
                .height(8.dp)
                .width(w)
                .clip(CircleShape)
                .background(c))
        }
    }
}

/* ---------- Page 1: SMS → record ---------- */

@Composable
private fun HeroPage(active: Boolean, parallax: Float) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            t.snapTo(0f); t.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        } else t.snapTo(0f)
    }
    val p1 = (t.value * 1.7f).coerceIn(0f, 1f)
    val p2 = ((t.value - 0.35f) / 0.65f).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.graphicsLayer {
            alpha = p1; translationX = (1f - p1) * -30f + parallax * 50f
        }) { SmsBubble() }
        Spacer(Modifier.height(11.dp))
        ArrowBadge(Modifier.graphicsLayer { alpha = p1 })
        Spacer(Modifier.height(12.dp))
        Box(Modifier.graphicsLayer {
            alpha = p2; translationY = (1f - p2) * 28f; scaleX = lerp(0.96f, 1f, p2); scaleY =
            lerp(0.96f, 1f, p2)
        }) { RecordCard() }
        Spacer(Modifier.height(30.dp))
        Eyebrow("WELCOME TO MONIES")
        Spacer(Modifier.height(12.dp))
        OnbHeadline("Sorted the\nmoment it lands")
        Spacer(Modifier.height(14.dp))
        OnbBody(
            "Every M-PESA message becomes a clean record — automatically, no typing.",
            Modifier.widthIn(max = 300.dp)
        )
    }
}

@Composable
private fun SmsBubble() {
    val shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 5.dp)
    Column(
        Modifier
            .widthIn(max = 290.dp)
            .clip(shape)
            .background(OnbColors.SmsBg)
            .border(1.dp, OnbColors.Rule, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(OnbColors.Emerald))
            Text(
                "M-PESA · TODAY 2:02 PM",
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                fontSize = 9.5.sp,
                letterSpacing = 1.2.sp,
                color = OnbColors.Muted
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = OnbColors.Coral)) { append("WEF6X3K9LM") }
                append(" Confirmed. Ksh1,500.00 paid to NAIVAS LIMITED. New M-PESA balance is Ksh8,420.00.")
            },
            fontFamily = MaterialTheme.typography.labelSmall.fontFamily, fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFF34302A),
        )
    }
}

@Composable
private fun ArrowBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.width(290.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            "Monies reads it",
            fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
            color = OnbColors.EmeraldDeep,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(OnbColors.Mint)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Canvas(Modifier
            .weight(1f)
            .height(2.dp)) {
            drawLine(
                OnbColors.Mint,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))
            )
        }
        Text("↓", color = OnbColors.Muted, fontSize = 14.sp)
    }
}

@Composable
private fun RecordCard() {
    Row(
        Modifier
            .widthIn(max = 300.dp)
            .onbCard(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(OnbColors.Mint),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "N",
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = OnbColors.EmeraldDeep
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Naivas Limited",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = OnbColors.Ink
            )
            Text(
                "Pay bill · 14:02",
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                fontSize = 11.5.sp,
                color = OnbColors.Muted
            )
        }
        Text(
            "−1,500",
            fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = OnbColors.Neg
        )
    }
}

/* ---------- Page 2: live rows ---------- */

private data class AutoRowData(
    val avatar: Color,
    val glyph: String,
    val tag: String,
    val name: String,
    val sub: String,
    val amount: String,
    val amountColor: Color
)

@Composable
private fun AutoPage(active: Boolean) {
    val rows = listOf(
        AutoRowData(
            OnbColors.Emerald,
            "↗",
            "RECV",
            "Salary",
            "Received · just now",
            "+48,000",
            OnbColors.Pos
        ),
        AutoRowData(
            OnbColors.Coral,
            "↘",
            "SEND",
            "Naivas",
            "Pay bill · 14:02",
            "−1,500",
            OnbColors.Neg
        ),
        AutoRowData(
            OnbColors.EmeraldDeep,
            "F",
            "FULIZA",
            "Available",
            "Updated 2:02 PM",
            "1,000",
            OnbColors.Ink
        ),
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PulseDot(); Text(
            "LIVE",
            fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = OnbColors.Emerald
        )
        }
        Spacer(Modifier.height(12.dp))
        OnbHeadline("Nothing\nto type")
        Spacer(Modifier.height(14.dp))
        OnbBody(
            "Transactions, balances, Fuliza and M-Shwari stay current as messages arrive.",
            Modifier.widthIn(max = 300.dp)
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            rows.forEachIndexed { i, r ->
                var show by remember(active) { mutableStateOf(false) }
                LaunchedEffect(active) {
                    if (active) {
                        delay((120L * i).milliseconds); show = true
                    } else show = false
                }
                AnimatedVisibility(
                    show,
                    enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 4 }) {
                    AutoRow(
                        r
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoRow(r: AutoRowData) {
    Row(
        Modifier
            .fillMaxWidth()
            .onbCard(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(r.avatar),
            contentAlignment = Alignment.Center
        ) {
            Text(
                r.glyph,
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White
            )
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    r.tag,
                    fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.8.sp,
                    color = OnbColors.EmeraldDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OnbColors.Mint)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
                Text(
                    r.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = OnbColors.Ink
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(r.sub, fontFamily = MaterialTheme.typography.labelSmall.fontFamily, fontSize = 11.sp, color = OnbColors.Muted)
        }
        Text(
            r.amount,
            fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = r.amountColor
        )
    }
}

@Composable
private fun PulseDot() {
    val tr = rememberInfiniteTransition(label = "pulse")
    val a by tr.animateFloat(
        1f,
        0.3f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "a"
    )
    Box(Modifier
        .size(7.dp)
        .clip(CircleShape)
        .background(OnbColors.Emerald.copy(alpha = a)))
}

/* ---------- Page 3: analytics dashboard ---------- */

@Composable
private fun DashboardPage() {
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
        RankedPreviewRow("Groceries", 42, OnbColors.Emerald)
        RankedPreviewRow("Transport", 28, OnbColors.Coral)
        RankedPreviewRow("Bills", 18, OnbColors.EmeraldDeep)
    }
}

@Composable
private fun RankedPreviewRow(label: String, pct: Int, color: Color) {
    Column {
        Row {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = OnbColors.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                "$pct%",
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                fontSize = 12.sp,
                color = OnbColors.Muted
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(OnbColors.Rule)
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
            fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = OnbColors.Muted
        )
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            letterSpacing = (-0.5).sp,
            color = OnbColors.Ink
        )
        if (change != null) {
            Spacer(Modifier.height(4.dp)); Text(
                change,
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = OnbColors.Neg
            )
        }
        if (sub != null) {
            Spacer(Modifier.height(4.dp)); Text(
                sub,
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                fontSize = 11.sp,
                color = OnbColors.Muted
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
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = OnbColors.Muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowLegend(
                    OnbColors.Emerald,
                    "In"
                ); FlowLegend(OnbColors.Coral, "Out")
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
        Text(label, fontFamily = MaterialTheme.typography.labelSmall.fontFamily, fontSize = 10.sp, color = OnbColors.Muted)
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
                        .background(OnbColors.Emerald)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(outF)
                        .clip(barShape)
                        .background(OnbColors.Coral)
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
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = OnbColors.EmeraldDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(OnbColors.Mint)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

/* ---------- Page 4: permissions ---------- */

@Composable
private fun PermissionsPage(active: Boolean) {
    val rationales = permissionRationales()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Eyebrow("ONE LAST STEP")
        Spacer(Modifier.height(12.dp))
        OnbHeadline("A few permissions", fontSize = 28.sp)
        Spacer(Modifier.height(10.dp))
        OnbBody(
            "Monies runs entirely on your device. Here's why it asks.",
            Modifier.widthIn(max = 320.dp),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(20.dp))
        Column {
            rationales.forEachIndexed { i, r ->
                var show by remember(active) { mutableStateOf(false) }
                LaunchedEffect(active) {
                    if (active) {
                        delay((120L * i).milliseconds); show = true
                    } else show = false
                }
                AnimatedVisibility(
                    show,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }) {
                    OnbPermissionRow(
                        r
                    )
                }
            }
        }
    }
}

@Composable
private fun OnbPermissionRow(item: PermissionRationale) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OnbColors.Mint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(item.icon),
                null,
                tint = OnbColors.EmeraldDeep,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = OnbColors.Ink
                )
                OnbMarker(item.isRequired)
            }
            Spacer(Modifier.height(3.dp))
            Text(item.description, fontSize = 13.sp, lineHeight = 18.sp, color = OnbColors.Muted)
        }
    }
}

@Composable
private fun OnbMarker(required: Boolean) {
    Text(
        if (required) "REQUIRED" else "OPTIONAL",
        fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.5.sp,
        letterSpacing = 0.6.sp,
        color = if (required) Color.White else OnbColors.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (required) OnbColors.Coral else OnbColors.ChipOpt)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}