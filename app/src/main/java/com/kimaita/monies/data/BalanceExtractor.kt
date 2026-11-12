package com.kimaita.monies.data

import java.util.concurrent.ConcurrentHashMap

object BalanceExtractor {
    private val BALANCE_GROUPS = listOf("MpesaBal", "Balance", "balance", "bal")
    private val FALLBACK = Regex(
        """(?:new\s+m-?pesa\s+balance\s+is|balance\s+is|bal[:.\s]+)\s*ksh?\.?\s*([\d,]+(?:\.\d{1,2})?)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val cache = ConcurrentHashMap<String, Regex>()

    fun extract(message: String, pattern: String?): Double? {
        if (!pattern.isNullOrBlank()) {
            val rx = cache.getOrPut(pattern) {
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            }
            rx.find(message)?.let { m ->
                for (g in BALANCE_GROUPS) {
                    val v =
                        runCatching { m.groups[g]?.value }.getOrNull()  // missing group throws — guard it
                    parseAmount(v)?.let { return it }
                }
            }
        }
        return parseAmount(FALLBACK.find(message)?.groupValues?.getOrNull(1))
    }

    private fun parseAmount(s: String?): Double? =
        s?.replace(",", "")?.trim()?.toDoubleOrNull()
}