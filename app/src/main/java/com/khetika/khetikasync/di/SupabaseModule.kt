package com.khetika.khetikasync.di

import android.util.Log
import com.khetika.khetikasync.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import javax.inject.Singleton

private const val TAG = "KhetikaSupabase"

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        Log.d(TAG, "Init: url='$url' keyLength=${key.length} keyPrefix='${key.take(12)}...'")
        if (url.isBlank() || key.isBlank()) {
            Log.e(TAG, "Supabase URL or key is blank — check local.properties + sync Gradle")
        }
        return createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Postgrest)
            install(Storage)
        }
    }

    @Provides
    @Singleton
    fun providePostgrest(client: SupabaseClient): Postgrest = client.postgrest

    @Provides
    @Singleton
    fun provideStorage(client: SupabaseClient): Storage = client.storage
}
