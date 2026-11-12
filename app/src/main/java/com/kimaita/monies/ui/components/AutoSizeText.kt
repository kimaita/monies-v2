package com.kimaita.monies.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    minFontSize: TextUnit = 13.sp,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val resolved = remember(text, style, fontWeight, widthPx, maxLines) {
            var candidate = style.merge(TextStyle(fontWeight = fontWeight))
            if (candidate.fontSize == TextUnit.Unspecified) candidate = candidate.copy(fontSize = 16.sp)
            while (candidate.fontSize > minFontSize) {
                val r = measurer.measure(
                    AnnotatedString(text),
                    style = candidate,
                    maxLines = maxLines,
                    constraints = Constraints(maxWidth = widthPx),
                )
                if (!r.hasVisualOverflow) break
                candidate = candidate.copy(fontSize = candidate.fontSize * 0.94f)
            }
            candidate
        }
        Text(text, style = resolved, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}