package com.maakthi.prescriptionpharmacist.data.repository

import com.maakthi.prescriptionpharmacist.core.assistant.AnswerGuard
import com.maakthi.prescriptionpharmacist.core.assistant.EvidenceRetriever
import com.maakthi.prescriptionpharmacist.data.local.dao.AssistantLogDao
import com.maakthi.prescriptionpharmacist.data.local.entity.AssistantLogEntity
import com.maakthi.prescriptionpharmacist.data.remote.AssistantApi
import com.maakthi.prescriptionpharmacist.data.remote.dto.ChatMessageDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.ChatRequestDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.EvidenceDto
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AssistantAnswer
import com.maakthi.prescriptionpharmacist.domain.model.AssistantLogEntry
import com.maakthi.prescriptionpharmacist.domain.model.ChatMessage
import com.maakthi.prescriptionpharmacist.domain.model.ChatRole
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.AssistantRepository
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import com.maakthi.prescriptionpharmacist.domain.repository.DrugKnowledgeRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAG pipeline: retrieve evidence from the local drug DB + official sources →
 * (refuse immediately when evidence is insufficient) → ask the secure backend
 * to answer FROM the evidence only → guard the reply (citation whitelist,
 * referral notices, disclaimer) → log the Q&A locally.
 */
@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val api: AssistantApi,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val drugEntryRepository: DrugEntryRepository,
    private val drugKnowledgeRepository: DrugKnowledgeRepository,
    private val assistantLogDao: AssistantLogDao
) : AssistantRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun ask(
        question: String,
        history: List<ChatMessage>
    ): AppResult<AssistantAnswer> {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) {
            return AppResult.error(ErrorCode.VALIDATION, "empty_question")
        }
        val language = AnswerGuard.languageOf(trimmed)

        // ---- 1) retrieval: local DB + official knowledge sources ----
        val drugs = runCatching { drugEntryRepository.observeDrugs().first() }.getOrDefault(emptyList())
        val query = EvidenceRetriever.knowledgeQuery(trimmed, drugs)
        val knowledge = runCatching { drugKnowledgeRepository.search(query) }.getOrNull()
        val pack = EvidenceRetriever.retrieve(trimmed, drugs, knowledge, language)

        // ---- 2) no reliable evidence → explicit refusal, no model call ----
        if (pack.insufficient) {
            val answer = AnswerGuard.finalize(trimmed, "", pack)
            log(trimmed, answer, language)
            return AppResult.success(answer)
        }

        // ---- 3) backend answers FROM the evidence only ----
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
            } + ChatMessageDto(role = "user", content = trimmed),
            evidence = pack.items.map {
                EvidenceDto(
                    id = it.id,
                    text = it.text,
                    sourceName = it.sourceName,
                    sourceUrl = it.sourceUrl,
                    sourceDate = it.sourceDate
                )
            },
            language = language
        )
        val authorization = authRepository.getIdToken()?.let { "Bearer $it" }

        val rawReply = try {
            val response = api.chat(
                url = "${baseUrl.trimEnd('/')}/chat",
                body = payload,
                authorization = authorization
            )
            when {
                response.code() == 429 ->
                    return AppResult.error(ErrorCode.RATE_LIMITED)
                !response.isSuccessful && response.code() in 401..403 ->
                    return AppResult.error(ErrorCode.UNAUTHORIZED)
                !response.isSuccessful && response.code() >= 500 ->
                    return AppResult.error(ErrorCode.SERVER)
                !response.isSuccessful ->
                    return AppResult.error(ErrorCode.ANALYSIS_FAILED)
                else -> response.body()?.reply?.trim()
            }
        } catch (e: IOException) {
            return AppResult.error(ErrorCode.NETWORK, cause = e)
        } catch (e: SerializationException) {
            return AppResult.error(ErrorCode.SERVER, cause = e)
        } catch (e: Exception) {
            return AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }

        if (rawReply.isNullOrEmpty()) {
            return AppResult.error(ErrorCode.ANALYSIS_FAILED, "Empty reply")
        }

        // ---- 4) guard: citations from evidence only + safety notices ----
        val answer = AnswerGuard.finalize(trimmed, rawReply, pack)
        log(trimmed, answer, language)
        return AppResult.success(answer)
    }

    override fun observeLog(): Flow<List<AssistantLogEntry>> =
        assistantLogDao.observeAll().map { list ->
            list.map {
                AssistantLogEntry(
                    id = it.id,
                    question = it.question,
                    answer = it.answer,
                    citations = runCatching {
                        json.decodeFromString<List<com.maakthi.prescriptionpharmacist.domain.model.AssistantCitation>>(it.citationsJson)
                    }.getOrDefault(emptyList()),
                    insufficient = it.insufficient,
                    language = it.language,
                    timestamp = it.timestamp
                )
            }
        }

    override suspend fun clearLog() {
        assistantLogDao.clear()
    }

    private suspend fun log(question: String, answer: AssistantAnswer, language: String) {
        assistantLogDao.insert(
            AssistantLogEntity(
                id = UUID.randomUUID().toString(),
                question = question,
                answer = answer.reply,
                citationsJson = json.encodeToString(answer.citations),
                insufficient = answer.insufficient,
                language = language,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
