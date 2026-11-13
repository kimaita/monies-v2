package com.kimaita.monies.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    // ---- text in the message: names, codes, dates, phone and account numbers ----

    @Test
    fun namesAreOnePrefixPhraseEach() {
        assertEquals("\"Jane*\" \"Doe*\"", ftsQueryOf("  Jane   Doe "))
        assertEquals("\"ACMEUTIL*\" \"token*\"", ftsQueryOf("ACMEUTIL, token!"))
    }

    @Test
    fun transactionCodeIsOneTerm() {
        assertEquals("\"VJ58A7HW0F*\"", ftsQueryOf("VJ58A7HW0F"))
        assertEquals("\"sk57*\"", ftsQueryOf("sk57"))
    }

    @Test
    fun punctuationInsideAWordSplitsItIntoConsecutiveTerms() {
        // the index splits "Doe/AcmeShirt" and "Ksh1,500.00" at punctuation, so the query does too
        assertEquals("\"Doe AcmeShirt*\"", ftsQueryOf("Doe/AcmeShirt"))
        assertEquals("\"Ksh1 500*\"", ftsQueryOf("Ksh1,500"))
        assertEquals("\"Ksh1 500 00*\"", ftsQueryOf("Ksh1,500.00"))
        assertEquals("\"1 500*\"", ftsQueryOf("1,500"))
    }

    @Test
    fun datesAreMatchedAsConsecutiveParts() {
        assertEquals("\"13 11 26*\"", ftsQueryOf("13/11/26"))
        assertEquals("\"2 11 24*\" \"at*\" \"7 12*\"", ftsQueryOf("2/11/24 at 7:12"))
        assertEquals("\"10 15*\"", ftsQueryOf("10:15"))
    }

    @Test
    fun fourDigitYearIsMatchedAsMessagesWriteIt() {
        assertEquals("\"13 11 26*\"", ftsQueryOf("13/11/2026"))
        assertEquals("\"1 2 24*\"", ftsQueryOf("1.2.2024"))
        // not a date: the last part is not a four-digit year
        assertEquals("\"1 678 543*\"", ftsQueryOf("1,678,543"))
    }

    @Test
    fun phoneNumbersMatchWhetherOrNotTheMessageSplitsThem() {
        assertEquals("\"0700000001*\"", ftsQueryOf("0700000001"))
        assertEquals("\"254700000001*\"", ftsQueryOf("254700000001"))
        // "0700-000001" is two terms, or the one token the message has
        assertEquals("\"0700 000001*\" OR \"0700000001*\"", ftsQueryOf("0700-000001"))
        assertEquals(
            "\"Jane*\" \"0700 000001*\" OR \"Jane*\" \"0700000001*\"",
            ftsQueryOf("Jane 0700-000001")
        )
        assertEquals("\"712 257 678*\" OR \"712257678*\"", ftsQueryOf("712-257-678"))
        // too short to be a phone number
        assertEquals("\"1 500*\"", ftsQueryOf("1,500"))
        // spaces separate words, each a prefix
        assertEquals("\"0700*\" \"000*\" \"001*\"", ftsQueryOf("0700 000 001"))
    }

    @Test
    fun emptyOrSymbolOnlyQueryHasNoFilter() {
        assertNull(ftsQueryOf(""))
        assertNull(ftsQueryOf("   "))
        assertNull(ftsQueryOf("!? -"))
        assertNull(subjectLikeOf(""))
        assertNull(subjectLikeOf("!? -"))
        assertNull(amountQueryOf("!? -"))
    }

    @Test
    fun operatorWordsAreJustWords() {
        assertEquals("\"OR*\" \"NOT*\"", ftsQueryOf("OR NOT"))
    }

    // ---- amounts ----

    @Test
    fun numericQueryIsAnAmountPrefix() {
        assertEquals("1500", amountQueryOf("1500"))
        assertEquals("2500.5", amountQueryOf(" 2,500.5 "))
        assertEquals("1500.", amountQueryOf("1500."))
        assertNull(amountQueryOf("jane"))
        assertNull(amountQueryOf(""))
        assertNull(amountQueryOf("12ab"))
        assertNull(amountQueryOf("NaN"))
    }

    @Test
    fun amountAsTheMessageWritesItMatchesTheStoredNumber() {
        assertEquals("1500.0", amountQueryOf("1,500.00"))
        assertEquals("1500.5", amountQueryOf("1,500.50"))
        assertEquals("1234.56", amountQueryOf("Ksh1,234.56"))
        assertEquals("50", amountQueryOf("Ksh 50"))
        assertEquals("700", amountQueryOf("KES700"))
        assertNull(amountQueryOf("Ksh"))
    }

    @Test
    fun aNumberSearchesTheMessageAndTheAmount() {
        val terms = searchTermsOf("1500")
        assertEquals("\"1500*\"", terms.fts)
        assertEquals("1500", terms.amount)
        val amountWithKsh = searchTermsOf("Ksh1,500")
        assertEquals("\"Ksh1 500*\"", amountWithKsh.fts)
        assertEquals("1500", amountWithKsh.amount)
    }

    // ---- subjects the user renamed ----

    @Test
    fun subjectNamesMatchWordsInOrder() {
        assertEquals("%jane%", subjectLikeOf("jane"))
        assertEquals("%Jane%Doe%", subjectLikeOf("  Jane   Doe "))
        assertEquals("%Mama%Mboga%", subjectLikeOf("Mama, Mboga!"))
    }

    @Test
    fun subjectPatternHasNoWildcardsFromTheUser() {
        assertEquals("%100%", subjectLikeOf("100%"))
        assertEquals("%a%b%", subjectLikeOf("a_ b"))
        assertNull(subjectLikeOf("%_"))
    }

    @Test
    fun oneSearchBoxTextGivesEveryForm() {
        val terms = searchTermsOf("Mama Mboga")
        assertEquals("\"Mama*\" \"Mboga*\"", terms.fts)
        assertNull(terms.amount)
        assertEquals("%Mama%Mboga%", terms.subjectLike)
        assertEquals(SearchTerms(null, null, null), searchTermsOf(null))
        assertEquals(SearchTerms(null, null, null), searchTermsOf("  "))
    }
}
