package com.kimaita.monies.data

import com.kimaita.monies.data.database.models.categoryKey
import com.kimaita.monies.data.database.models.defaultCategoryId
import com.kimaita.monies.data.database.models.defaultCategoryRules
import com.kimaita.monies.data.database.models.getDefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoryRulesTest {

    private val ids = getDefaultCategories().mapIndexed { i, c -> categoryKey(c.name) to i + 1 }.toMap()
    private val names = ids.entries.associate { (name, id) -> id to name }

    private fun categoryOf(name: String, from: Map<String, Int> = ids) =
        defaultCategoryId(name, from)?.let { names[it] }

    @Test
    fun everyRuleNamesAShippedCategory() {
        defaultCategoryRules.forEach { assertTrue(it.category, categoryKey(it.category) in ids) }
    }

    @Test
    fun knownMerchantsLandInTheirCategory() {
        listOf(
            "KPLC PREPAID" to "electricity",
            "KPLC POSTPAID" to "electricity",
            "Kenya Power and Lighting Co Ltd" to "electricity",
            "QUIDEXPLUS" to "transport",
            "QUIDEX PLUS LIMITED" to "transport",
            "SM705" to "transport",
            "sm2000" to "transport",
            "SUPER METRO LTD" to "transport",
            "NICCO MOVERS KCA 123A" to "transport",
            "Nicco Movers KDJ456B" to "transport",
            "JAVA HOUSE AFRICA VALLEY ARCADE" to "meals",
            "JAVAHOUSE" to "meals",
            "ART CAFFE WESTGATE" to "meals",
            "ARTCAFFE" to "meals",
            "Art Cafe" to "meals",
            "GALITOS" to "meals",
            "Galito's Juja" to "meals",
            "SAFARICOM HOME" to "internet",
            "SAFARICOM HOME FIBRE" to "internet",
            "Safaricom Home Data" to "internet",
            "SAFARICOM DATA BUNDLES" to "airtime & data",
            "SAFARICOM OFFERS" to "airtime & data",
            "airtime" to "airtime & data",
            "Airtime" to "airtime & data",
            "Airtel Networks Kenya Ltd" to "airtime & data",
            "Airtel Networks" to "airtime & data",
        ).forEach { (name, expected) -> assertEquals(name, expected, categoryOf(name)) }
    }

    @Test
    fun lookalikesAreLeftAlone() {
        listOf(
            "SAFARICOM PLC", "AIRTEL MONEY", "SMITH JOHN", "SM 45", "ASM12 TRADERS",
            "JAVAN OTIENO", "SMART CAFE", "PAUL KAMAU", "KPLCX", "M-Shwari account", "Airtime Hub Ltd",
        ).forEach { assertNull(it, categoryOf(it)) }
    }

    @Test
    fun aMissingCategoryIsSkippedAndCaseIsIgnored() {
        assertNull(categoryOf("JAVA HOUSE", from = ids - "meals"))
        assertEquals("electricity", categoryOf("KPLC PREPAID", from = ids - "meals"))
        assertEquals("airtime & data", categoryKey("  AIRTIME & Data "))
    }
}
