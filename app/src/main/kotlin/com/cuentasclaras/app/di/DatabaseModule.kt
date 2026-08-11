package com.cuentasclaras.app.di

import android.content.Context
import androidx.room.Room
import com.cuentasclaras.app.data.local.CacheDao
import com.cuentasclaras.app.data.local.CuentasClarasDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CuentasClarasDatabase {
        return Room.databaseBuilder(
            context,
            CuentasClarasDatabase::class.java,
            "cuentas_claras_cache.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCacheDao(database: CuentasClarasDatabase): CacheDao = database.cacheDao()
}
