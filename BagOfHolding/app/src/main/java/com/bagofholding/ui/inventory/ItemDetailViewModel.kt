package com.bagofholding.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.repository.InventoryRepository
import com.bagofholding.di.AppContainer
import com.bagofholding.ui.characters.CharacterListViewModel.Companion.STOP_TIMEOUT_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val isLoading: Boolean = true,
    /** null after loading means the item was deleted. */
    val item: InventoryItem? = null,
    /** Items inside, when the item is a storage compartment. */
    val contents: List<InventoryItem> = emptyList(),
)

/** Read-only description of one inventory item or storage compartment, with edit and delete. */
class ItemDetailViewModel(
    private val itemId: Long,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    val uiState: StateFlow<ItemDetailUiState> = inventoryRepository.observeDetails(itemId)
        .map { ItemDetailUiState(isLoading = false, item = it?.item, contents = it?.contents.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ItemDetailUiState())

    fun save(item: InventoryItem) {
        viewModelScope.launch { inventoryRepository.save(item.copy(id = itemId)) }
    }

    fun delete() {
        viewModelScope.launch { inventoryRepository.delete(itemId) }
    }

    companion object {
        fun factory(container: AppContainer, itemId: Long) = viewModelFactory {
            initializer { ItemDetailViewModel(itemId, container.inventoryRepository) }
        }
    }
}
