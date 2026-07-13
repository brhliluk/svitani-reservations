package cz.svitaninymburk.projects.reservations.android.repository.auth

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface AuthLocalDataSource {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun getUserJson(): String?
    suspend fun saveAuth(accessToken: String, refreshToken: String, userJson: String)
    suspend fun clearAuth()
}

class AuthLocalDataSourceImpl(
    private val dataStore: DataStore<AuthData>
) : AuthLocalDataSource {

    override suspend fun getAccessToken(): String? =
        dataStore.data.map { it.accessToken }.first()

    override suspend fun getRefreshToken(): String? =
        dataStore.data.map { it.refreshToken }.first()

    override suspend fun getUserJson(): String? =
        dataStore.data.map { it.userJson }.first()

    override suspend fun saveAuth(accessToken: String, refreshToken: String, userJson: String) {
        dataStore.updateData {
            it.copy(accessToken = accessToken, refreshToken = refreshToken, userJson = userJson)
        }
    }

    override suspend fun clearAuth() {
        dataStore.updateData { AuthData() }
    }
}
