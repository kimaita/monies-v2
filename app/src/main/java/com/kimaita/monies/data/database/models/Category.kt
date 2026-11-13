package com.kimaita.monies.data.database.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
@Immutable
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String? = null,
    val monthlyBudget: Double? = null
)

fun getDefaultCategories(): List<Category> {
    return listOf(
        Category(name = "Airtime & Data"),
        Category(name = "Transport"),
        Category(name = "Fuel"),
        Category(name = "Groceries"),
        Category(name = "Shopping"),
        Category(name = "Meals"),
        Category(name = "Gifts"),
        Category(name = "Rent"),
        Category(name = "Internet"),
        Category(name = "Electricity"),
        Category(name = "Entertainment"),
        Category(name = "Health"),
        Category(name = "Education"),
        Category(name = "Miscellaneous"),
    )
}

/** Puts subjects whose parsed name matches [pattern] (case-insensitive) in [category]. */
class CategoryRule(val category: String, pattern: String) {
    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
}

/**
 * Built-in rules, matched against a subject's `rawName`. The first rule whose category still exists
 * wins; categories are found by name, ignoring case, so a renamed or deleted one is skipped. A new
 * subject gets its category when a sync creates it (SmsSyncer); subjects that already exist without
 * one are checked once each time these rules change (DatabaseInitializer). A category the user
 * picked is never replaced.
 */
val defaultCategoryRules: List<CategoryRule> = listOf(
    CategoryRule("Electricity", """\bKPLC\b|\bKENYA POWER\b"""),
    // Quidex Plus prepays Bolt; Super Metro buses are "SM705", "SM2000"; Nicco Movers names carry a plate.
    CategoryRule("Transport", """\bQUIDEX|^SM\d|\bSUPER\s*METRO\b|\bNICCO\s*MOVERS\b"""),
    CategoryRule("Meals", """\bJAVA\s*HOUSE|\bART\s*CAF+E|\bGALITO"""),
    // Before Airtime & Data, so Safaricom Home fibre is never taken for a data bundle.
    CategoryRule("Internet", """\bSAFARICOM\s*HOME\b"""),
    // M-PESA and Airtel airtime parse as "airtime"; Airtel bundles are paid to Airtel Networks.
    CategoryRule(
        "Airtime & Data",
        """^AIRTIME$|\bSAFARICOM\b.*\b(?:DATA|BUNDLES?|OFFERS?|AIRTIME)\b|\bAIRTEL NETWORKS\b""",
    ),
)

/** Changes whenever a rule does, so existing subjects are checked again after an update. */
val defaultCategoryRulesVersion: String
    get() = defaultCategoryRules.joinToString("\n") { "${it.category}=${it.regex.pattern}" }
        .hashCode().toString()

/** Lookup key for [defaultCategoryId]'s map: a category name, trimmed and lower-cased. */
fun categoryKey(name: String) = name.trim().lowercase()

/** The built-in category id for a subject parsed as [rawName]; [categoryIds] is keyed by [categoryKey]. */
fun defaultCategoryId(rawName: String, categoryIds: Map<String, Int>): Int? =
    defaultCategoryRules.firstNotNullOfOrNull { rule ->
        categoryIds[categoryKey(rule.category)]?.takeIf { rule.regex.containsMatchIn(rawName) }
    }
