package com.kimaita.monies.models

/** A mobile-money service and the SMS sender addresses it uses. Stored by enum name. */
enum class Provider(val displayName: String, val senders: List<String>) {
    MPESA("M-PESA", listOf("MPESA", "M-Shwari")),
    AIRTEL("Airtel Money", listOf("airtelmoney"));

    /** True when a message the patterns did not match still looks like one of this provider's transactions. */
    fun looksLikeTransaction(body: String): Boolean = when (this) {
        MPESA -> MPESA_CODE.containsMatchIn(body)
        AIRTEL -> body.contains("Ksh", ignoreCase = true) &&
            AIRTEL_MARKERS.any { body.contains(it, ignoreCase = true) }
    }

    companion object {
        private val MPESA_CODE = Regex("^\\s*[A-Z0-9]{10}\\s+confirmed", RegexOption.IGNORE_CASE)

        private val AIRTEL_MARKERS = listOf("Bal", "TID", "Txn ID")

        fun forSender(address: String?): Provider? =
            entries.firstOrNull { p -> p.senders.any { it.equals(address, ignoreCase = true) } }

        val allSenders: List<String> get() = entries.flatMap { it.senders }
    }
}
