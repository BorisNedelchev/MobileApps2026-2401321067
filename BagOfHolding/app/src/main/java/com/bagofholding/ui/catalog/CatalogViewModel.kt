package com.bagofholding.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.repository.CatalogRepository
import com.bagofholding.data.repository.InventoryRepository
import com.bagofholding.di.AppContainer
import com.bagofholding.ui.characters.CharacterListViewModel.Companion.STOP_TIMEOUT_MS
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CatalogEvent {
    /** [returnToInventory] is true when the row itself was tapped, false for the "+" button. */
    data class AddedToInventory(val item: InventoryItem, val returnToInventory: Boolean) : CatalogEvent
    data class Deleted(val item: CatalogItem) : CatalogEvent
}

class CatalogViewModel(
    private val characterId: Long,
    private val catalogRepository: CatalogRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<ItemCategory?>(null)
    val category: StateFlow<ItemCategory?> = selectedCategory.asStateFlow()

    val canAddToInventory: Boolean get() = characterId > 0

    /** Search results; null until the first search finishes. */
    val items: StateFlow<List<CatalogItem>?> =
        combine(query, selectedCategory, catalogRepository.changes) { text, selected, _ ->
            catalogRepository.search(text, selected)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val eventChannel = Channel<CatalogEvent>(Channel.BUFFERED)
    val events: Flow<CatalogEvent> = eventChannel.receiveAsFlow()

    fun setQuery(text: String) {
        query.value = text
    }

    fun setCategory(category: ItemCategory?) {
        selectedCategory.value = category
    }

    /**
     * Adds one unit to the character. The screen closes only after the write finished
     * (closing first would cancel viewModelScope and could lose the item).
     */
    fun addToInventory(item: CatalogItem, returnToInventory: Boolean = false) {
        if (!canAddToInventory) return
        viewModelScope.launch {
            val added = inventoryRepository.addFromCatalog(characterId, item)
            eventChannel.send(CatalogEvent.AddedToInventory(added, returnToInventory))
        }
    }

    fun saveCustom(item: CatalogItem) {
        viewModelScope.launch { catalogRepository.saveCustom(item) }
    }

    fun deleteCustom(item: CatalogItem) {
        viewModelScope.launch {
            if (catalogRepository.deleteCustom(item.id)) eventChannel.send(CatalogEvent.Deleted(item))
        }
    }

    companion object {
        fun factory(container: AppContainer, characterId: Long) = viewModelFactory {
            initializer {
                CatalogViewModel(characterId, container.catalogRepository, container.inventoryRepository)
            }
        }
    }
}
