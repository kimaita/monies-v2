package com.kimaita.monies.models

/** A mobile-money service and the SMS sender addresses it uses. Stored by enum name. */
enum class Provider(val displayName: String, val senders: List<String>) {
    MPESA("M-PESA", listOf("MPESA", "M-Shwari"));

    companion object {
        fun forSender(address: String?): Provider? =
            entries.firstOrNull { p -> p.senders.any { it.equals(address, ignoreCase = true) } }

        val allSenders: List<String> get() = entries.flatMap { it.senders }
    }
}
