package com.jack.meuholerite.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jack.meuholerite.model.Candidatura
import com.jack.meuholerite.repository.CandidaturaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminViewModel(private val repository: CandidaturaRepository = CandidaturaRepository()) : ViewModel() {

    var isAdmin by mutableStateOf<Boolean?>(null)
    var isLoading by mutableStateOf(false)

    private val _candidaturas = MutableStateFlow<List<Candidatura>>(emptyList())
    val candidaturas = _candidaturas.asStateFlow()

    var selectedCandidatura by mutableStateOf<Candidatura?>(null)

    fun checkAdminStatus() {
        viewModelScope.launch {
            isLoading = true
            isAdmin = repository.isAdmin()
            isLoading = false
        }
    }

    fun loadCandidaturas() {
        viewModelScope.launch {
            isLoading = true
            try {
                val snapshot = repository.getCandidaturasQuery().get().await()
                _candidaturas.value = snapshot.toObjects(Candidatura::class.java)
            } catch (e: Exception) {
                // handle error
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleExportado(id: String, current: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateExportado(id, !current)
                loadCandidaturas()
                if (selectedCandidatura?.id == id) {
                    selectedCandidatura = selectedCandidatura?.copy(exportado = !current)
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun deleteCandidatura(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteCandidatura(id)
                loadCandidaturas()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun loadCandidaturaDetail(id: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                selectedCandidatura = repository.getCandidaturaById(id)
            } catch (e: Exception) {
                // handle error
            } finally {
                isLoading = false
            }
        }
    }
}
