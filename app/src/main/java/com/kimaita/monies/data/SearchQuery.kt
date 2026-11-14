package com.kimaita.monies.data

/**
 * The FTS4 MATCH expression for a search box. Each word is split on anything that is not a letter
 * or digit, because the index (simple tokenizer) splits the message the same way: "13/11/26" is the
 * phrase `"13 11 26*"`, "Ksh1,500" is `"Ksh1 500*"`. The last part of each word is a prefix. A date
 * with a four-digit year is matched on its two-digit year, as messages write it. A phone number
 * written in parts ("0712-345-678": digits only, nine or more in all) may also be one token in the
 * message, so the glued form is offered as an alternative of the whole expression. Null when empty.
 */
fun ftsQueryOf(query: String): String? {
    val words = query.trim().split(Regex("\\s+"))
        .map { word -> word.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() } }
        .filter { it.isNotEmpty() }
        .map { parts -> if (isDateWithLongYear(parts)) parts.dropLast(1) + parts.last().takeLast(2) else parts }
    if (words.isEmpty()) return null
    fun phrase(parts: List<String>) = "\"${parts.joinToString(" ")}*\""
    val exact = words.joinToString(" ") { phrase(it) }
    val glued = words.joinToString(" ") { parts ->
        phrase(if (isPhoneInParts(parts)) listOf(parts.joinToString("")) else parts)
    }
    return if (glued == exact) exact else "$exact OR $glued"
}

private fun isPhoneInParts(parts: List<String>) =
    parts.size > 1 && parts.all { p -> p.all(Char::isDigit) } && parts.sumOf { it.length } >= 9

private fun isDateWithLongYear(parts: List<String>) =
    parts.size == 3 && parts.all { p -> p.all(Char::isDigit) } &&
            parts[0].length <= 2 && parts[1].length <= 2 && parts[2].length == 4

/**
 * The query as an amount prefix when it is a number, else null. Commas and a leading Ksh/KES are
 * dropped, and a written-out ".00" matches how amounts are stored ("1,500.00" is "1500.0").
 */
fun amountQueryOf(query: String): String? {
    val number = query.trim().replace(",", "").replace(Regex("^(?i)(ksh|kes)\\.?\\s*"), "")
    if (!Regex("\\d+(\\.\\d*)?").matches(number)) return null
    val dot = number.indexOf('.')
    if (dot < 0 || dot == number.length - 1) return number
    return number.substring(0, dot + 1) + number.substring(dot + 1).trimEnd('0').ifEmpty { "0" }
}

/** A LIKE pattern for subject display names: the words in order, anywhere in the name. Null when empty. */
fun subjectLikeOf(query: String): String? =
    query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("%", "%", "%")

/** What one search box text becomes in SQL; the list and the export build it the same way. */
data class SearchTerms(val fts: String?, val amount: String?, val subjectLike: String?)

fun searchTermsOf(query: String?): SearchTerms {
    val q = query.orEmpty()
    return SearchTerms(ftsQueryOf(q), amountQueryOf(q), subjectLikeOf(q))
}
