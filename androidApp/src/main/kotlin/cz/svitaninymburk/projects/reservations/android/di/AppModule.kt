package cz.svitaninymburk.projects.reservations.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import cz.svitaninymburk.projects.reservations.android.api.provideHttpClient
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthData
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthDataSerializer
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthLocalDataSource
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthLocalDataSourceImpl
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepository
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val appModule = module {
    single { provideHttpClient() }
    single { provideEncryptedDataStore(androidContext()) }
    single<AuthLocalDataSource> { AuthLocalDataSourceImpl(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
}

private fun provideEncryptedDataStore(context: Context): DataStore<AuthData> {
    AeadConfig.register()
    val keysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context, "reservations_keyset", "reservations_keyset_prefs")
        .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
        .withMasterKeyUri("android-keystore://reservations_master_key")
        .build()
        .keysetHandle

    val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)

    return DataStoreFactory.create(
        serializer = AeadSerializer(
            aead = aead,
            wrappedSerializer = AuthDataSerializer,
            associatedData = "auth_prefs".encodeToByteArray(),
        ),
        produceFile = { File(context.filesDir, "datastore/auth_prefs.json") }
    )
}
