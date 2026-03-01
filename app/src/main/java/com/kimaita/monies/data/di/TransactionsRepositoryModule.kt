package com.kimaita.monies.data.di

import com.kimaita.monies.data.DefaultTransactionsRepository
import com.kimaita.monies.data.TransactionsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionsRepositoryModule {

    @Binds
    abstract fun bindTransactionsRepository(
        defaultTransactionsRepository: DefaultTransactionsRepository

    ): TransactionsRepository
}