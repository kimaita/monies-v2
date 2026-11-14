package com.kimaita.monies.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupScreenTest {

    private val running = SmsSyncProgress.Running(processed = 40, total = 100, year = 2024)

    @Test
    fun `nothing is shown until WorkManager has answered`() {
        assertEquals(StartupScreen.Blank, startupScreen(null, null))
        assertEquals(StartupScreen.Blank, startupScreen(null, true))
        assertEquals(StartupScreen.Blank, startupScreen(null, false))
    }

    @Test
    fun `a running import shows its progress whatever the cursor says`() {
        val expected = StartupScreen.Import(processed = 40, total = 100, year = 2024)
        assertEquals(expected, startupScreen(running, null))
        assertEquals(expected, startupScreen(running, true))
        assertEquals(expected, startupScreen(running, false))
    }

    @Test
    fun `failed or cancelled import shows the retry screen`() {
        assertEquals(StartupScreen.Import(failed = true), startupScreen(SmsSyncProgress.Failed, true))
        assertEquals(StartupScreen.Import(failed = true), startupScreen(SmsSyncProgress.Failed, false))
    }

    @Test
    fun `a finished import shows the app even if the cursor read is stale`() {
        assertEquals(StartupScreen.App, startupScreen(SmsSyncProgress.Done, true))
        assertEquals(StartupScreen.App, startupScreen(SmsSyncProgress.Done, null))
    }

    @Test
    fun `with no first-run work the cursor decides`() {
        assertEquals(StartupScreen.Blank, startupScreen(SmsSyncProgress.Idle, null))
        assertEquals(StartupScreen.Import(), startupScreen(SmsSyncProgress.Idle, true))
        assertEquals(StartupScreen.App, startupScreen(SmsSyncProgress.Idle, false))
    }

    @Test
    fun `the import starts only on a first run with no first-run work`() {
        assertTrue(shouldStartFirstRunImport(SmsSyncProgress.Idle, true))
        assertFalse(shouldStartFirstRunImport(SmsSyncProgress.Idle, false))
        assertFalse(shouldStartFirstRunImport(SmsSyncProgress.Idle, null))
        assertFalse(shouldStartFirstRunImport(null, true))
        assertFalse(shouldStartFirstRunImport(running, true))
        assertFalse(shouldStartFirstRunImport(SmsSyncProgress.Failed, true))
        assertFalse(shouldStartFirstRunImport(SmsSyncProgress.Done, true))
    }

    @Test
    fun `a finished import is not undone when the first-run work disappears`() {
        assertEquals(SmsSyncProgress.Done, keepDone(SmsSyncProgress.Done, SmsSyncProgress.Idle))
        assertEquals(SmsSyncProgress.Idle, keepDone(null, SmsSyncProgress.Idle))
        assertEquals(SmsSyncProgress.Idle, keepDone(SmsSyncProgress.Failed, SmsSyncProgress.Idle))
        assertEquals(running, keepDone(SmsSyncProgress.Done, running))
    }
}
