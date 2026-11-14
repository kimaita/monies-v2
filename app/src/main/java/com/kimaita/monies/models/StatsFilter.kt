package com.kimaita.monies.models

/**
 * The five values every totals and breakdown query narrows by. Empty lists are passed as null,
 * which the queries read as "no restriction".
 */
data class StatsFilter(
    val start: String?,
    val end: String?,
    val subjects: List<Long>? = null,
    val categories: List<Int>? = null,
    val types: List<Int>? = null,
)
