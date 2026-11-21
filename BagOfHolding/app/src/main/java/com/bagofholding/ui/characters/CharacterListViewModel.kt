package com.bagofholding.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bagofholding.data.model.CharacterWithStats
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.repository.CharacterRepository
import com.bagofholding.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CharacterListViewModel(private val repository: CharacterRepository) : ViewModel() {

    /** null while the first query is running. */
    val characters: StateFlow<List<CharacterWithStats>?> = repository.observeCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun save(character: PlayerCharacter) {
        viewModelScope.launch { repository.save(character) }
    }

    fun delete(characterId: Long) {
        viewModelScope.launch { repository.delete(characterId) }
    }

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CharacterListViewModel(container.characterRepository) }
        }
    }
}
