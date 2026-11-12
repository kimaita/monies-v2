package com.kimaita.monies.di

import com.kimaita.monies.data.export.CsvWriter
import com.kimaita.monies.data.export.FormatWriter
import com.kimaita.monies.data.export.JsonWriter
import com.kimaita.monies.data.export.PdfWriter
import com.kimaita.monies.data.export.XlsxWriter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    // Multibinding: every FormatWriter feeds the Set<FormatWriter> that ExportWorker injects.
    // Adding a format later = one new class + one @Binds line here.
    @Binds
    @IntoSet
    abstract fun bindCsv(w: CsvWriter): FormatWriter

    @Binds
    @IntoSet
    abstract fun bindJson(w: JsonWriter): FormatWriter

    @Binds
    @IntoSet
    abstract fun bindXlsx(w: XlsxWriter): FormatWriter
    @Binds
    @IntoSet
    abstract fun bindPdf(w: PdfWriter): FormatWriter

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}