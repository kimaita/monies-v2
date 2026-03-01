package com.kimaita.monies.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.kimaita.monies.ui.theme.GreenIncome
import com.kimaita.monies.ui.theme.RedExpense
import com.kimaita.monies.ui.theme.currencyFontFamily
import java.text.NumberFormat
import java.util.Currency
import kotlin.math.absoluteValue

@Composable
fun CurrencyText(
    amount: Double,
    modifier: Modifier = Modifier,
    currency: String = "KES",
    isInc: Boolean? = null,
    content: @Composable (text: String) -> Unit = { text ->
        Text(
            text = text,
            color = if (text.startsWith("+")) {
                GreenIncome
            } else if (text.startsWith("-")) {
                RedExpense
            } else {
                Color.Unspecified
            },
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(maxFontSize = LocalTextStyle.current.fontSize)
        )
    }
) {
    val formattedAmount = remember(amount, currency) {
        val format = NumberFormat.getCurrencyInstance(java.util.Locale("en", "KE"))
        try {
            format.currency = Currency.getInstance(currency)
        } catch (e: Exception) {
            format.currency = Currency.getInstance("KES")
        }
        format.format(amount.absoluteValue)
    }

    val prefix = when (isInc) {
        true -> "+"
        false -> "-"
        null -> ""
    }

    val textToShow = "$prefix$formattedAmount"
    content(textToShow)
}

@Preview
@Composable
fun CurrencyTextPreview() {
    CurrencyText(4500.88, isInc = false) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview
@Composable
fun CurrencyTextDefaultPreview() {
    CurrencyText(4500.88, isInc = true)
}

@Preview
@Composable
fun CurrencyTextNoIncPreview() {
    CurrencyText(40500.88)
}
