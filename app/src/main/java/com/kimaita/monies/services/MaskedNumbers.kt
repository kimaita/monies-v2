package com.kimaita.monies.services

/**
 * M-PESA hides some digits of a person's number ("0712***880"). The visible digits stay the same
 * for one person, so a masked number is joined to the saved subject whose full number it could be.
 */

/** True when [full] could be [masked] with its hidden digits shown: same length, same digit wherever [masked] has one. */
fun fitsMask(masked: String, full: String): Boolean =
    '*' in masked && '*' !in full && full.length == masked.length &&
            masked.indices.all { masked[it] == '*' || masked[it] == full[it] }

/** The one saved subject a masked number stands for: same raw name, a number that fits. Null for none or several. */
fun unmaskedSubject(rawName: String, number: String, saved: Collection<SavedSubject>): SavedSubject? =
    saved.filter { it.rawName == rawName && fitsMask(number, it.number) }.distinctBy { it.id }.singleOrNull()

private val NAME_THEN_MASKED_NUMBER = Regex("""^(.+) (\d+\*+\d+)$""")

/** A raw name saved before the patterns knew masked numbers ("JANE DOE 0712***880"), as name and number. */
fun splitMaskedName(rawName: String): Pair<String, String>? =
    NAME_THEN_MASKED_NUMBER.find(rawName)?.destructured?.let { (name, number) -> name to number }
