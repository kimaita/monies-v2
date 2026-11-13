package com.kimaita.monies.models

/** A mobile-money service and the SMS sender addresses it uses. Stored by enum name. */
enum class Provider(val displayName: String, val senders: List<String>) {
    MPESA("M-PESA", listOf("MPESA", "M-Shwari"));

    /** True when a message the patterns did not match still looks like one of this provider's transactions. */
    fun looksLikeTransaction(body: String): Boolean = when (this) {
        MPESA -> MPESA_CODE.containsMatchIn(body)
    }

    companion object {
        private val MPESA_CODE = Regex("^\\s*[A-Z0-9]{10}\\s+confirmed", RegexOption.IGNORE_CASE)

        fun forSender(address: String?): Provider? =
            entries.firstOrNull { p -> p.senders.any { it.equals(address, ignoreCase = true) } }

        val allSenders: List<String> get() = entries.flatMap { it.senders }
    }
}
