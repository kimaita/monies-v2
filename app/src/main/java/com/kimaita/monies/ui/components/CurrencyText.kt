package com.kimaita.monies.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import java.text.NumberFormat
import java.util.Currency
import kotlin.math.absoluteValue

/**
 * Shared across rows. NumberFormat is not thread-safe, so this is only touched from the main
 * thread, inside composition.
 */
private val kesFormat: NumberFormat =
    NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("en-KE")).apply {
        currency = Currency.getInstance("KES")
    }

@Composable
fun CurrencyText(
    amount: Double,
    modifier: Modifier = Modifier,
    isInc: Boolean? = null,
    content: @Composable (text: String) -> Unit
) {
    val formattedAmount = remember(amount) { kesFormat.format(amount.absoluteValue) }

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
