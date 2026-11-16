package com.kimaita.monies.ui.charts

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimaita.monies.ui.util.formatAmount

/** A box of a [FlowChart]: its name, amount and colour. */
@Immutable
data class FlowBox(val name: String, val amount: Double, val color: Color)

/** Geometry in dp, from the design's 326-wide card. */
private const val MID_TOP = 28f
private const val MID_H = 124f
private const val BAR_MIN = 4f
/**
 * A slot is at least this tall, so with the gap two neighbouring labels' centres are 26 (left, two
 * lines) and 13 (right, one line) apart.
 */
private const val LEFT_SLOT = 8f
private const val LEFT_GAP = 18f
private const val RIGHT_SLOT = 5f
private const val RIGHT_GAP = 8f
/** Half a label's height: two lines on the left, one on the right. */
private const val LEFT_LABEL_HALF = 13f
private const val RIGHT_LABEL_HALF = 7f
private const val LEFT_BAR_X = 92f / 326f
private const val MID_X = 150f / 326f
private const val RIGHT_BAR_X = 212f / 326f
private const val BAR_W = 6f
private const val MID_W = 10f
private const val LABEL_GAP = 6f

/** One side's boxes: where each bar sits, and where its ribbon meets the middle bar (all in dp). */
private class FlowColumn(val barTop: FloatArray, val barH: FloatArray, val midTop: FloatArray, val midH: FloatArray) {
    /** The lowest a label reaches: half its height below its bar's centre. */
    fun bottom(labelHalf: Float): Float =
        if (barTop.isEmpty()) 0f else barTop.last() + barH.last() / 2 + labelHalf
}

/**
 * Bars are as tall as their share of [total] but at least [BAR_MIN], each in a slot tall enough for
 * its label; the column is centred on the middle bar, starting no higher than just above it. Where
 * the ribbons meet the middle bar they are exactly their share, one after another.
 */
private fun flowColumn(amounts: List<Double>, total: Double, slotMin: Float, gap: Float): FlowColumn {
    val n = amounts.size
    val unit = (MID_H / total).toFloat()
    val barH = FloatArray(n) { maxOf(amounts[it].toFloat() * unit, BAR_MIN) }
    val slot = FloatArray(n) { maxOf(barH[it], slotMin) }
    val span = slot.sum() + gap * (n - 1).coerceAtLeast(0)
    var y = maxOf(MID_TOP + MID_H / 2 - span / 2, MID_TOP - 8f)
    val barTop = FloatArray(n)
    for (i in 0 until n) {
        barTop[i] = y + (slot[i] - barH[i]) / 2
        y += slot[i] + gap
    }
    var m = MID_TOP
    val midTop = FloatArray(n)
    val midH = FloatArray(n) { amounts[it].toFloat() * unit }
    for (i in 0 until n) {
        midTop[i] = m
        m += midH[i]
    }
    return FlowColumn(barTop, barH, midTop, midH)
}

/**
 * Money from the [left] boxes pooling in the middle bar, named [middle], and out to the [right]
 * boxes, each ribbon as thick as its amount. Both sides should add up to the same total. With no
 * money on either side it draws an empty middle with dashed "In 0" and "Out 0" stubs.
 */
@Composable
fun FlowChart(
    left: List<FlowBox>,
    right: List<FlowBox>,
    middle: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val nameFont = MaterialTheme.typography.bodySmall.fontFamily
    val ink = cs.onSurface
    val muted = cs.onSurfaceVariant
    val track = cs.surfaceContainerHighest
    val outline = cs.outline

    val total = maxOf(left.sumOf { it.amount }, right.sumOf { it.amount })
    val empty = total <= 0.0
    val columns = remember(left, right) {
        if (empty) null
        else flowColumn(left.map { it.amount }, total, LEFT_SLOT, LEFT_GAP) to
                flowColumn(right.map { it.amount }, total, RIGHT_SLOT, RIGHT_GAP)
    }
    val heightDp = if (columns == null) 84f
    else maxOf(
        columns.first.bottom(LEFT_LABEL_HALF), columns.second.bottom(RIGHT_LABEL_HALF), MID_TOP + MID_H,
    ) + 6f

    Spacer(
        modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .semantics { contentDescription = description }
            .drawWithCache {
                val d = density
                val nameStyle = TextStyle(
                    fontFamily = nameFont, fontSize = 11.sp, lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold, color = if (empty) muted else ink,
                )
                val amountStyle = TextStyle(fontFamily = ChartFont, fontSize = 11.sp, lineHeight = 13.sp, color = muted)
                val w = size.width
                val leftBar = w * LEFT_BAR_X
                val midX = w * MID_X
                val rightBar = w * RIGHT_BAR_X
                val barW = BAR_W * d
                val midW = MID_W * d
                val labelGap = LABEL_GAP * d
                val leftLabelW = (leftBar - labelGap).toInt().coerceAtLeast(1)
                val rightLabelX = rightBar + barW + labelGap
                val rightLabelW = (w - rightLabelX).toInt().coerceAtLeast(1)
                fun one(text: String, style: TextStyle, maxW: Int) = measurer.measure(
                    text, style, overflow = TextOverflow.Ellipsis, maxLines = 1,
                    constraints = Constraints(maxWidth = maxW),
                )

                val middleLabel = one(middle, nameStyle, w.toInt())
                val middleAt = Offset(midX + midW / 2 - middleLabel.size.width / 2f, (MID_TOP - 7f) * d - middleLabel.size.height)

                if (columns == null) {
                    val inName = one("In", nameStyle, leftLabelW)
                    val inAmt = one("0", amountStyle, leftLabelW)
                    val outLine = one("Out", nameStyle, rightLabelW)
                    val outAmt = one("0", amountStyle, rightLabelW)
                    val dash = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * d, 3 * d)))
                    val stubTop = 40.5f * d
                    val stubSize = Size(5 * d, 23 * d)
                    val stubR = CornerRadius(2 * d)
                    val nameTop = stubTop + 2 * d
                    return@drawWithCache onDrawBehind {
                        drawText(middleLabel, topLeft = middleAt)
                        drawRoundRect(track, Offset(midX, MID_TOP * d), Size(midW, 48 * d), CornerRadius(3 * d))
                        drawRoundRect(outline, Offset(leftBar + 0.5f * d, stubTop), stubSize, stubR, style = dash)
                        drawRoundRect(outline, Offset(rightBar + 0.5f * d, stubTop), stubSize, stubR, style = dash)
                        drawText(inName, topLeft = Offset(leftBar - labelGap - inName.size.width, nameTop))
                        drawText(inAmt, topLeft = Offset(leftBar - labelGap - inAmt.size.width, nameTop + inName.size.height))
                        drawText(outLine, topLeft = Offset(rightLabelX, nameTop))
                        drawText(outAmt, topLeft = Offset(rightLabelX, nameTop + outLine.size.height))
                    }
                }

                val (lc, rc) = columns
                fun ribbon(x0: Float, top0: Float, bottom0: Float, x1: Float, top1: Float, bottom1: Float) = Path().apply {
                    val mx = (x0 + x1) / 2
                    moveTo(x0, top0)
                    cubicTo(mx, top0, mx, top1, x1, top1)
                    lineTo(x1, bottom1)
                    cubicTo(mx, bottom1, mx, bottom0, x0, bottom0)
                    close()
                }

                val leftRibbons = left.indices.map { i ->
                    ribbon(
                        leftBar + barW, lc.barTop[i] * d, (lc.barTop[i] + lc.barH[i]) * d,
                        midX, lc.midTop[i] * d, (lc.midTop[i] + lc.midH[i]) * d,
                    )
                }
                val rightRibbons = right.indices.map { i ->
                    ribbon(
                        midX + midW, rc.midTop[i] * d, (rc.midTop[i] + rc.midH[i]) * d,
                        rightBar, rc.barTop[i] * d, (rc.barTop[i] + rc.barH[i]) * d,
                    )
                }
                val leftNames = left.map { one(it.name, nameStyle, leftLabelW) }
                val leftAmounts = left.map { one(formatAmount(it.amount), amountStyle, leftLabelW) }
                val leftLabelTop = left.indices.map { i ->
                    val c = (lc.barTop[i] + lc.barH[i] / 2) * d
                    c - (leftNames[i].size.height + leftAmounts[i].size.height) / 2f
                }
                val rightLabels = right.map {
                    measurer.measure(
                        buildAnnotatedString {
                            append(it.name)
                            append(' ')
                            withStyle(SpanStyle(fontFamily = ChartFont, fontWeight = FontWeight.Normal, color = muted)) {
                                append(formatAmount(it.amount))
                            }
                        },
                        nameStyle, overflow = TextOverflow.Ellipsis, maxLines = 1,
                        constraints = Constraints(maxWidth = rightLabelW),
                    )
                }
                val barR = CornerRadius(2 * d)

                onDrawBehind {
                    leftRibbons.forEachIndexed { i, p -> drawPath(p, left[i].color, alpha = 0.3f) }
                    rightRibbons.forEachIndexed { i, p -> drawPath(p, right[i].color, alpha = 0.3f) }
                    drawRoundRect(ink, Offset(midX, MID_TOP * d), Size(midW, MID_H * d), CornerRadius(3 * d))
                    drawText(middleLabel, topLeft = middleAt)
                    for (i in left.indices) {
                        drawRoundRect(left[i].color, Offset(leftBar, lc.barTop[i] * d), Size(barW, lc.barH[i] * d), barR)
                        val n = leftNames[i]
                        val a = leftAmounts[i]
                        drawText(n, topLeft = Offset(leftBar - labelGap - n.size.width, leftLabelTop[i]))
                        drawText(a, topLeft = Offset(leftBar - labelGap - a.size.width, leftLabelTop[i] + n.size.height))
                    }
                    for (i in right.indices) {
                        drawRoundRect(right[i].color, Offset(rightBar, rc.barTop[i] * d), Size(barW, rc.barH[i] * d), barR)
                        val l = rightLabels[i]
                        val c = (rc.barTop[i] + rc.barH[i] / 2) * d
                        drawText(l, topLeft = Offset(rightLabelX, c - l.size.height / 2f))
                    }
                }
            }
    )
}
