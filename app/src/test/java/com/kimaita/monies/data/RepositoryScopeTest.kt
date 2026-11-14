package com.kimaita.monies.data

import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Singleton

class RepositoryScopeTest {
    /**
     * The selected account lives in the repository, so Home and Analytics must share one instance.
     * Without @Singleton every view model got its own copy and a choice on Home never reached Analytics.
     */
    @Test
    fun repositoryIsSingleton() =
        assertTrue(TransactionsRepository::class.java.isAnnotationPresent(Singleton::class.java))
}
