package com.caloly.app.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloly.app.domain.social.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SocialUiState(
    val query: String = "",
    val searchResults: List<SocialProfile> = emptyList(),
    val requests: List<IncomingFollowRequest> = emptyList(),
    val connections: List<SocialConnection> = emptyList(),
    val summaries: Map<String, SharedDailySummary?> = emptyMap(),
    val selected: SocialConnection? = null,
    val selectedSummary: SharedDailySummary? = null,
    val selectedGoals: List<RelationshipGoal> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val repository: SocialRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state

    init { refresh() }

    fun setQuery(value: String) { _state.value = _state.value.copy(query = value) }

    fun search() = runAction {
        val q = _state.value.query.trim()
        _state.value = _state.value.copy(searchResults = repository.searchUsers(q))
    }

    fun refresh() = runAction {
        val requests = repository.incomingRequests()
        val connections = repository.connections()
        val date = LocalDate.now().toString()
        val summaries = coroutineScope {
            connections.map { connection ->
                async { connection.relationshipId to repository.sharedToday(connection.profile.id, date) }
            }.awaitAll().toMap()
        }
        _state.value = _state.value.copy(requests = requests, connections = connections, summaries = summaries)
    }

    fun sendRequest(profile: SocialProfile, relationshipType: String = "FRIEND") = runAction {
        repository.sendFollowRequest(profile.id, relationshipType)
        _state.value = _state.value.copy(message = if (relationshipType == "PARTNER") "Partner isteği gönderildi" else "Takip isteği gönderildi")
        val q = _state.value.query.trim()
        if (q.length >= 2) _state.value = _state.value.copy(searchResults = repository.searchUsers(q))
    }

    fun respond(request: IncomingFollowRequest, accept: Boolean) = runAction {
        repository.respondToRequest(request.requestId, accept)
        _state.value = _state.value.copy(message = if (accept) "Takip isteği kabul edildi" else "Takip isteği reddedildi")
        reloadSocial()
    }

    fun openConnection(connection: SocialConnection) = runAction {
        val date = LocalDate.now().toString()
        val summary = repository.sharedToday(connection.profile.id, date)
        val goals = repository.goals(connection.relationshipId, date)
        _state.value = _state.value.copy(selected = connection, selectedSummary = summary, selectedGoals = goals)
    }

    fun closeConnection() {
        _state.value = _state.value.copy(selected = null, selectedSummary = null, selectedGoals = emptyList())
    }

    fun updateSharing(permissions: SharingPermissions) = runAction {
        val connection = _state.value.selected ?: return@runAction
        repository.updateSharing(connection.relationshipId, permissions)
        val updated = connection.copy(mySharing = permissions)
        _state.value = _state.value.copy(
            selected = updated,
            connections = _state.value.connections.map { if (it.relationshipId == updated.relationshipId) updated else it },
            message = "Paylaşım izinleri güncellendi",
        )
    }

    fun createStepGoal(target: Int) = createGoal(GoalMetric.STEPS_DAILY, target)
    fun createCalorieGoal() = createGoal(GoalMetric.CALORIE_TARGET, 1)

    private fun createGoal(metric: GoalMetric, target: Int) = runAction {
        val connection = _state.value.selected ?: return@runAction
        repository.createGoal(connection.relationshipId, metric, target)
        val goals = repository.goals(connection.relationshipId, LocalDate.now().toString())
        _state.value = _state.value.copy(selectedGoals = goals, message = "Ortak hedef eklendi")
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    private suspend fun reloadSocial() {
        val requests = repository.incomingRequests()
        val connections = repository.connections()
        val date = LocalDate.now().toString()
        val summaries = coroutineScope {
            connections.map { connection -> async { connection.relationshipId to repository.sharedToday(connection.profile.id, date) } }
                .awaitAll().toMap()
        }
        _state.value = _state.value.copy(requests = requests, connections = connections, summaries = summaries)
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            runCatching { block() }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "İşlem tamamlanamadı") }
            _state.value = _state.value.copy(loading = false)
        }
    }
}
