package cz.svitaninymburk.projects.reservations.android.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cz.svitaninymburk.projects.reservations.android.api.provideHttpClient
import cz.svitaninymburk.projects.reservations.android.repository.AuthRepository
import cz.svitaninymburk.projects.reservations.android.repository.AuthRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { provideHttpClient() }
    single<SharedPreferences> { provideEncryptedPrefs(androidContext()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
}

private fun provideEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    return EncryptedSharedPreferences.create(
        context,
        "reservations_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
