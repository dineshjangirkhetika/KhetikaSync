package com.khetika.khetikasync.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrefsModule {

    @Provides
    @Singleton
    fun provideAuthPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("khetika_auth", Context.MODE_PRIVATE)
}
