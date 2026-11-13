package com.kimaita.monies.models

data class Category(val id: Int, val name: String)

/** The `category_apply_mode` setting: what picking a category on a transaction does. */
enum class CategoryApplyMode(val key: String) {
    ASK("ask"), ONE("one"), ALL("all");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: ASK
    }
}

/**
 * Whether a pick applies to every transaction of the subject (true), only the one (false), or the
 * user must be asked (null). A transaction without a subject can only be set alone.
 */
fun applyToAllOfSubject(mode: CategoryApplyMode, hasSubject: Boolean): Boolean? = when {
    !hasSubject -> false
    mode == CategoryApplyMode.ASK -> null
    else -> mode == CategoryApplyMode.ALL
}
