package com.randwise.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.randwise.app.AppContainer
import com.randwise.app.ai.ChatTurn
import com.randwise.app.ai.CoachRequest
import com.randwise.app.ai.ContextBuilder
import com.randwise.app.ai.LocalCoach
import com.randwise.app.data.model.ChatMessageEntity
import com.randwise.app.domain.Analysis
import com.randwise.app.domain.FinanceSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CoachSource { USER, GEMINI, LOCAL }

data class ChatMessage(val source: CoachSource, val text: String, val highlights: List<String> = emptyList(), val note: String? = null)

data class CoachState(val messages: List<ChatMessage> = emptyList(), val sending: Boolean = false)

/**
 * Level 3 AI Coach. Sends only anonymised, pre-computed facts to the gateway when the user has
 * opted in; otherwise (or when offline) answers deterministically on-device. Conversations are
 * kept in the encrypted database so they survive restarts.
 */
class CoachViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CoachState())
    val state: StateFlow<CoachState> = _state.asStateFlow()

    private val repo get() = container.repository

    init {
        viewModelScope.launch {
            val saved = runCatching { repo.chatHistory() }.getOrDefault(emptyList()).map { it.toMessage() }
            _state.update { it.copy(messages = saved + it.messages) }
        }
    }

    private fun append(message: ChatMessage) {
        _state.update { it.copy(messages = it.messages + message) }
        viewModelScope.launch { runCatching { repo.saveChat(message.toEntity()) } }
    }

    fun ask(question: String, snapshot: FinanceSnapshot, analysis: Analysis) {
        val q = question.trim()
        if (q.isEmpty() || _state.value.sending) return
        append(ChatMessage(CoachSource.USER, q))
        _state.update { it.copy(sending = true) }
        val settings = container.preferences.settings.value
        viewModelScope.launch {
            val reply = if (settings.consentAiInterpretation && settings.gatewayUrl.isNotBlank()) {
                runCatching {
                    val history = _state.value.messages.dropLast(1).takeLast(6).map {
                        ChatTurn(if (it.source == CoachSource.USER) "user" else "model", it.text.take(600))
                    }
                    val req = CoachRequest(ContextBuilder.sanitizeQuestion(q), ContextBuilder.build(snapshot, analysis), history)
                    container.coachClient.ask(settings.gatewayUrl, req)
                }.fold(
                    onSuccess = { ChatMessage(CoachSource.GEMINI, it.answer, it.highlights, it.disclaimer.ifBlank { LocalCoach.DISCLAIMER }) },
                    onFailure = {
                        val local = LocalCoach.answer(q, analysis)
                        ChatMessage(CoachSource.LOCAL, local.answer, note = "AI is busy or unreachable right now — answered on your phone. ${local.disclaimer}")
                    },
                )
            } else {
                val local = LocalCoach.answer(q, analysis)
                ChatMessage(CoachSource.LOCAL, local.answer, note = local.disclaimer)
            }
            append(reply)
            _state.update { it.copy(sending = false) }
        }
    }

    fun clear() {
        _state.update { CoachState() }
        viewModelScope.launch { runCatching { repo.clearChat() } }
    }

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        source = source.name, text = text, highlights = highlights.joinToString("\n"), note = note, timestamp = System.currentTimeMillis(),
    )

    private fun ChatMessageEntity.toMessage() = ChatMessage(
        source = runCatching { CoachSource.valueOf(source) }.getOrDefault(CoachSource.LOCAL),
        text = text,
        highlights = highlights.split('\n').filter { it.isNotBlank() },
        note = note,
    )
}
