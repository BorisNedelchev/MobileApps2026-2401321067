package com.bagofholding.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.model.toCatalogItem
import com.bagofholding.data.repository.CatalogRepository
import com.bagofholding.data.repository.CharacterRepository
import com.bagofholding.data.repository.InventoryRepository
import com.bagofholding.data.repository.QuantityChange
import com.bagofholding.di.AppContainer
import com.bagofholding.domain.InventoryCalculator
import com.bagofholding.domain.InventoryStats
import com.bagofholding.domain.InventoryTextFormatter
import com.bagofholding.ui.characters.CharacterListViewModel.Companion.STOP_TIMEOUT_MS
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InventoryUiState(
    val isLoading: Boolean = true,
    val character: PlayerCharacter? = null,
    val items: List<InventoryItem> = emptyList(),
    val stats: InventoryStats? = null,
)

sealed interface InventoryEvent {
    /** An item disappeared (swipe or quantity reached 0); the screen offers "Undo". */
    data class ItemRemoved(val item: InventoryItem) : InventoryEvent
}

class InventoryViewModel(
    private val characterId: Long,
    private val characterRepository: CharacterRepository,
    private val inventoryRepository: InventoryRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    val uiState: StateFlow<InventoryUiState> = combine(
        characterRepository.observeCharacter(characterId),
        inventoryRepository.observeInventory(characterId),
    ) { character, items ->
        InventoryUiState(
            isLoading = false,
            character = character,
            items = items,
            stats = character?.let { InventoryCalculator.stats(items, it.strength) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InventoryUiState())

    private val eventChannel = Channel<InventoryEvent>(Channel.BUFFERED)
    val events: Flow<InventoryEvent> = eventChannel.receiveAsFlow()

    fun changeQuantity(item: InventoryItem, delta: Int) {
        viewModelScope.launch {
            val result = inventoryRepository.changeQuantity(item.id, delta)
            if (result is QuantityChange.Removed) eventChannel.send(InventoryEvent.ItemRemoved(result.item))
        }
    }

    fun delete(item: InventoryItem) {
        viewModelScope.launch {
            inventoryRepository.delete(item.id)?.let { eventChannel.send(InventoryEvent.ItemRemoved(it)) }
        }
    }

    fun restore(item: InventoryItem) {
        viewModelScope.launch { inventoryRepository.restore(item) }
    }

    fun saveItem(item: InventoryItem, alsoSaveToCatalog: Boolean) {
        viewModelScope.launch {
            val owned = item.copy(characterId = characterId)
            inventoryRepository.save(owned)
            if (alsoSaveToCatalog) catalogRepository.saveCustom(owned.toCatalogItem())
        }
    }

    fun saveCharacter(character: PlayerCharacter) {
        viewModelScope.launch { characterRepository.save(character) }
    }

    /** Text for the Android share sheet, or null while loading. */
    fun shareText(): String? {
        val state = uiState.value
        val character = state.character ?: return null
        return InventoryTextFormatter.format(character, state.items)
    }

    companion object {
        fun factory(container: AppContainer, characterId: Long) = viewModelFactory {
            initializer {
                InventoryViewModel(
                    characterId = characterId,
                    characterRepository = container.characterRepository,
                    inventoryRepository = container.inventoryRepository,
                    catalogRepository = container.catalogRepository,
                )
            }
        }
    }
}
