package com.kimaita.monies.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskedNumbersTest {

    @Test
    fun aFullNumberFitsAMaskWithTheSameVisibleDigits() {
        assertTrue(fitsMask("0712***880", "0712345880"))
        assertFalse(fitsMask("0712***880", "0712345881"))
        assertFalse(fitsMask("0712***880", "254712345880"))
        assertFalse(fitsMask("0712***880", "0712***880"))
        assertFalse(fitsMask("0712345880", "0712345880"))
    }

    @Test
    fun aSubjectStandsForAMaskOnlyWhenItIsTheOneThatFits() {
        val a = SavedSubject("JANE DOE", "0712345880", 1, null)
        val aMerged = SavedSubject("JANE DOE", "0712000880", 1, null)
        val b = SavedSubject("JANE DOE", "0712999880", 2, null)
        val other = SavedSubject("JOHN DOE", "0712345880", 3, null)
        assertEquals(a, unmaskedSubject("JANE DOE", "0712***880", listOf(a, other)))
        assertEquals(1L, unmaskedSubject("JANE DOE", "0712***880", listOf(a, aMerged))?.id)
        assertNull(unmaskedSubject("JANE DOE", "0712***880", listOf(a, b)))
        assertNull(unmaskedSubject("JOHN DOE", "0799***880", listOf(other)))
    }

    @Test
    fun anOldRawNameSplitsIntoNameAndMaskedNumber() {
        assertEquals("JANE DOE" to "0712***880", splitMaskedName("JANE DOE 0712***880"))
        assertNull(splitMaskedName("JANE DOE"))
        assertNull(splitMaskedName("ACME BANK LIMITED- APP"))
    }
}
