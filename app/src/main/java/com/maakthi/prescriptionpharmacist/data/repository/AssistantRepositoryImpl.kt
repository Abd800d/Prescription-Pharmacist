package com.maakthi.prescriptionpharmacist.data.repository

import com.maakthi.prescriptionpharmacist.data.remote.AssistantApi
import com.maakthi.prescriptionpharmacist.data.remote.dto.ChatMessageDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.ChatRequestDto
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ChatMessage
import com.maakthi.prescriptionpharmacist.domain.model.ChatRole
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.AssistantRepository
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val api: AssistantApi,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : AssistantRepository {

    override suspend fun sendMessage(
        history: List<ChatMessage>,
        message: String
    ): AppResult<String> {
        val baseUrl = settingsRepository.settings.first().backendUrl
        if (baseUrl.isBlank()) {
            return AppResult.error(ErrorCode.NOT_CONFIGURED)
        }
        val payload = ChatRequestDto(
            messages = history.map {
                ChatMessageDto(
                    role = if (it.role == ChatRole.USER) "user" else "assistant",
                    content = it.content
                )
            } + ChatMessageDto(role = "user", content = message)
        )
        val authorization = authRepository.getIdToken()?.let { "Bearer $it" }

        return try {
            val response = api.chat(
                url = "${baseUrl.trimEnd('/')}/chat",
                body = payload,
                authorization = authorization
            )
            when {
                !response.isSuccessful && response.code() in 401..403 ->
                    AppResult.error(ErrorCode.UNAUTHORIZED)
                !response.isSuccessful && response.code() >= 500 ->
                    AppResult.error(ErrorCode.SERVER)
                !response.isSuccessful -> AppResult.error(ErrorCode.ANALYSIS_FAILED)
                else -> {
                    val reply = response.body()?.reply?.trim()
                    if (reply.isNullOrEmpty()) {
                        AppResult.error(ErrorCode.ANALYSIS_FAILED, "Empty reply")
                    } else {
                        AppResult.success(reply)
                    }
                }
            }
        } catch (e: IOException) {
            AppResult.error(ErrorCode.NETWORK, cause = e)
        } catch (e: SerializationException) {
            AppResult.error(ErrorCode.SERVER, cause = e)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }
    }
}
