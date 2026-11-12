package com.maakthi.prescriptionpharmacist.data.analysis

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppSettings
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.UserRole
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * API access policy: the backend base URL comes from settings, the auth
 * header carries the Firebase ID token (never an API key), and HTTP errors
 * map to friendly app errors.
 */
class BackendConfigTest {

    private class FakeSettings(var backendUrl: String = "") : SettingsRepository {
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings(backendUrl = backendUrl))
        override suspend fun setThemeMode(themeMode: com.maakthi.prescriptionpharmacist.domain.model.ThemeMode) {}
        override suspend fun setLanguage(language: com.maakthi.prescriptionpharmacist.domain.model.AppLanguage) {}
        override suspend fun setBackendUrl(url: String) { backendUrl = url }
        override suspend fun setOnboardingDone(done: Boolean) {}
        override suspend fun setMediaSyncEnabled(enabled: Boolean) {}
        override suspend fun setAutoBackupEnabled(enabled: Boolean) {}
        override suspend fun setLastSyncAt(at: Long) {}
        override suspend fun setLastBackupAt(at: Long) {}
        override suspend fun setDataConsentAt(at: Long) {}
        override suspend fun clearAll() {}
    }

    private class FakeAuth(var token: String? = null) : AuthRepository {
        override fun isAvailable(): Boolean = true
        override val currentUser: Flow<AppUser?> = MutableStateFlow(null)
        override suspend fun signInAnonymously(): AppResult<AppUser> = AppResult.error(ErrorCode.UNKNOWN)
        override suspend fun signInWithEmail(email: String, password: String): AppResult<AppUser> = AppResult.error(ErrorCode.UNKNOWN)
        override suspend fun registerWithEmail(email: String, password: String): AppResult<AppUser> = AppResult.error(ErrorCode.UNKNOWN)
        override suspend fun signInWithGoogleIdToken(idToken: String): AppResult<AppUser> = AppResult.error(ErrorCode.UNKNOWN)
        override suspend fun signOut() {}
        override suspend fun sendEmailVerification(): AppResult<Unit> = AppResult.success(Unit)
        override suspend fun sendPasswordReset(email: String): AppResult<Unit> = AppResult.success(Unit)
        override suspend fun reloadUser(): AppResult<AppUser?> = AppResult.success(null)
        override suspend fun deleteAccount(): AppResult<Unit> = AppResult.success(Unit)
        override suspend fun fetchRole(): UserRole = UserRole.USER
        override suspend fun getIdToken(): String? = token
    }

    @Test
    fun `base url is trimmed of trailing slashes`() = runTest {
        val config = BackendConfig(FakeSettings("https://api.example.com/"), FakeAuth())
        assertEquals("https://api.example.com", config.baseUrl())
    }

    @Test
    fun `authorization is a bearer of the firebase id token`() = runTest {
        val withToken = BackendConfig(FakeSettings("https://api.example.com"), FakeAuth("tok-123"))
        assertEquals("Bearer tok-123", withToken.authorization())

        val noToken = BackendConfig(FakeSettings("https://api.example.com"), FakeAuth(null))
        assertNull(noToken.authorization())
    }

    @Test
    fun `http errors map to the right app error codes`() {
        val config = BackendConfig(FakeSettings("https://api.example.com"), FakeAuth())
        fun codeOf(http: Int) = (config.errorForHttp(http) as AppResult.Error).code

        assertEquals(ErrorCode.UNAUTHORIZED, codeOf(401))
        assertEquals(ErrorCode.UNAUTHORIZED, codeOf(403))
        assertEquals(ErrorCode.SERVER, codeOf(500))
        assertEquals(ErrorCode.SERVER, codeOf(503))
        assertEquals(ErrorCode.ANALYSIS_FAILED, codeOf(400))
        assertEquals(ErrorCode.ANALYSIS_FAILED, codeOf(404))
    }

    @Test
    fun `the api key never travels from the app — only the id token`() = runTest {
        val config = BackendConfig(FakeSettings("https://api.example.com"), FakeAuth("id-token"))
        val header = config.authorization().orEmpty()
        assertEquals("Bearer id-token", header)
        // No raw key material shape — exactly "Bearer <id-token>"
        assertEquals("Bearer", header.split(" ").first())
    }
}
