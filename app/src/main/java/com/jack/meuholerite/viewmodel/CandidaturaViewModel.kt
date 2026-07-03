package com.jack.meuholerite.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.jack.meuholerite.model.Candidatura
import com.jack.meuholerite.repository.CandidaturaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CandidaturaViewModel(private val repository: CandidaturaRepository = CandidaturaRepository()) : ViewModel() {

    var nome by mutableStateOf("")
    var cidade by mutableStateOf("")
    var telefone by mutableStateOf("")
    var cargo by mutableStateOf("")
    var lgpdAccepted by mutableStateOf(false)
    var isSubmitting by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val _candidaturaId = MutableStateFlow<String?>(null)
    val candidaturaId = _candidaturaId.asStateFlow()

    fun reset() {
        nome = ""
        cidade = ""
        telefone = ""
        cargo = ""
        lgpdAccepted = false
        errorMessage = null
        _candidaturaId.value = null
    }

    fun submit(origem: String = "APP") {
        if (nome.isBlank() || cidade.isBlank() || telefone.length < 10 || cargo.isBlank() || !lgpdAccepted) {
            errorMessage = "Preencha todos os campos corretamente e aceite a LGPD."
            return
        }

        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            try {
                val auth = FirebaseAuth.getInstance()
                
                // Se não houver usuário (tela de login), entra como anônimo para permitir a gravação
                // Isso satisfaz a regra 'isSignedIn()' do Firestore
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }

                val telefoneDigits = telefone.filter { it.isDigit() }
                val candidatura = Candidatura(
                    nome = nome,
                    cidade = cidade,
                    telefoneDigits = telefoneDigits,
                    cargo = cargo,
                    origem = origem
                )
                val id = repository.saveCandidatura(candidatura)
                _candidaturaId.value = id
            } catch (e: Exception) {
                errorMessage = "Erro ao enviar: ${e.message}"
            } finally {
                isSubmitting = false
            }
        }
    }
}
