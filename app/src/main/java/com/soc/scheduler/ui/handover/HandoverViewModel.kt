package com.soc.scheduler.ui.handover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.Graph
import com.soc.scheduler.data.HandoverNote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HandoverViewModel : ViewModel() {

    private val repo = Graph.repo
    private val dao = repo.handoverDao

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter

    val notes: StateFlow<List<HandoverNote>> = combine(
        _query.flatMapLatest { q ->
            if (q.isBlank()) dao.observeAll() else dao.search("%$q%")
        },
        _categoryFilter,
    ) { list, category ->
        if (category == null) list else list.filter { it.category == category }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setCategory(value: String?) {
        _categoryFilter.value = value
    }

    fun save(note: HandoverNote) = viewModelScope.launch {
        dao.upsert(note)
    }

    fun delete(note: HandoverNote) = viewModelScope.launch {
        dao.delete(note.id)
    }
}
