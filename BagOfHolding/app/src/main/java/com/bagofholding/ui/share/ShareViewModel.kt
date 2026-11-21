package com.bagofholding.ui.share

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bagofholding.data.repository.CharacterRepository
import com.bagofholding.data.repository.InventoryRepository
import com.bagofholding.di.AppContainer
import com.bagofholding.network.AddressParser
import com.bagofholding.network.DiscoveredHost
import com.bagofholding.network.HostAddress
import com.bagofholding.network.LanShareServer
import com.bagofholding.network.NetworkUtils
import com.bagofholding.network.ServiceDiscovery
import com.bagofholding.network.ShareCodec
import com.bagofholding.network.ShareFetcher
import com.bagofholding.network.ShareFormatException
import com.bagofholding.network.SharePayload
import com.bagofholding.network.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class ShareUiState(
    /** True when the screen was opened from a character, so that character can be shared. */
    val canHost: Boolean = false,
    val characterName: String = "",
    val isHosting: Boolean = false,
    val hostAddresses: List<String> = emptyList(),
    val port: Int = 0,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val isFetching: Boolean = false,
    /** A downloaded inventory waiting for the user to choose "import" or "merge". */
    val received: SharePayload? = null,
)

enum class ShareError { INVALID_ADDRESS, CONNECTION_FAILED, BAD_DATA, HOST_FAILED }

sealed interface ShareEvent {
    data class Error(val error: ShareError) : ShareEvent
    data class Imported(val characterId: Long, val characterName: String, val merged: Boolean) : ShareEvent
}

class ShareViewModel(
    private val characterId: Long,
    private val characterRepository: CharacterRepository,
    private val inventoryRepository: InventoryRepository,
    private val discovery: ServiceDiscovery,
    private val client: ShareFetcher,
    private val localAddresses: suspend () -> List<String>,
    serverFactory: (payloadProvider: suspend () -> String) -> LanShareServer = { LanShareServer(it) },
) : ViewModel() {

    private val server = serverFactory { buildPayloadJson() }

    private val state = MutableStateFlow(ShareUiState(canHost = characterId > 0))
    val uiState: StateFlow<ShareUiState> = state.asStateFlow()

    private val eventChannel = Channel<ShareEvent>(Channel.BUFFERED)
    val events: Flow<ShareEvent> = eventChannel.receiveAsFlow()

    init {
        discovery.startDiscovery()
        viewModelScope.launch {
            discovery.hosts.collect { hosts -> state.update { it.copy(discoveredHosts = hosts) } }
        }
        if (characterId > 0) {
            viewModelScope.launch {
                val character = characterRepository.getCharacter(characterId)
                if (character != null) state.update { it.copy(characterName = character.name) }
            }
        }
    }

    fun startHosting() {
        if (!state.value.canHost || state.value.isHosting) return
        viewModelScope.launch {
            try {
                val port = server.start(viewModelScope)
                discovery.register(state.value.characterName.ifBlank { DEFAULT_HOST_NAME }, port)
                val addresses = localAddresses()
                state.update { it.copy(isHosting = true, port = port, hostAddresses = addresses) }
            } catch (e: IOException) {
                Log.w(TAG, "Could not start the share server", e)
                eventChannel.send(ShareEvent.Error(ShareError.HOST_FAILED))
            }
        }
    }

    fun stopHosting() {
        server.stop()
        discovery.unregister()
        state.update { it.copy(isHosting = false, port = 0, hostAddresses = emptyList()) }
    }

    fun fetchFromText(text: String) {
        val address = AddressParser.parse(text)
        if (address == null) {
            eventChannel.trySend(ShareEvent.Error(ShareError.INVALID_ADDRESS))
        } else {
            fetch(address)
        }
    }

    fun fetch(address: HostAddress) {
        if (state.value.isFetching) return
        state.update { it.copy(isFetching = true) }
        viewModelScope.launch {
            try {
                val payload = client.fetch(address)
                state.update { it.copy(received = payload) }
            } catch (e: ShareFormatException) {
                Log.w(TAG, "Invalid data from $address", e)
                eventChannel.send(ShareEvent.Error(ShareError.BAD_DATA))
            } catch (e: IOException) {
                Log.w(TAG, "Could not reach $address", e)
                eventChannel.send(ShareEvent.Error(ShareError.CONNECTION_FAILED))
            } finally {
                state.update { it.copy(isFetching = false) }
            }
        }
    }

    /** Creates a new character from the received inventory. */
    fun importAsNewCharacter() {
        val payload = takeReceived() ?: return
        viewModelScope.launch {
            val newId = characterRepository.save(payload.character.toDomain())
            inventoryRepository.importItems(newId, payload.items.map { it.toDomain(newId) }, merge = false)
            eventChannel.send(ShareEvent.Imported(newId, payload.character.name, merged = false))
        }
    }

    /** Adds the received items to the character this screen was opened from. */
    fun mergeIntoCurrentCharacter() {
        if (characterId <= 0) return
        val payload = takeReceived() ?: return
        viewModelScope.launch {
            inventoryRepository.importItems(characterId, payload.items.map { it.toDomain(characterId) }, merge = true)
            eventChannel.send(ShareEvent.Imported(characterId, state.value.characterName, merged = true))
        }
    }

    fun dismissReceived() {
        state.update { it.copy(received = null) }
    }

    private fun takeReceived(): SharePayload? {
        val payload = state.value.received
        state.update { it.copy(received = null) }
        return payload
    }

    private suspend fun buildPayloadJson(): String {
        val character = checkNotNull(characterRepository.getCharacter(characterId)) { "Character was deleted" }
        val items = inventoryRepository.getInventory(characterId)
        return ShareCodec.encode(ShareCodec.fromDomain(character, items))
    }

    override fun onCleared() {
        // Leaving the screen closes the server socket and NSD registrations: nothing keeps running.
        server.stop()
        discovery.unregister()
        discovery.stopDiscovery()
        super.onCleared()
    }

    companion object {
        private const val TAG = "ShareViewModel"
        private const val DEFAULT_HOST_NAME = "Adventurer"

        fun factory(container: AppContainer, characterId: Long) = viewModelFactory {
            initializer {
                ShareViewModel(
                    characterId = characterId,
                    characterRepository = container.characterRepository,
                    inventoryRepository = container.inventoryRepository,
                    discovery = container.createServiceDiscovery(),
                    client = container.shareClient,
                    localAddresses = { withContext(Dispatchers.IO) { NetworkUtils.localIpv4Addresses() } },
                )
            }
        }
    }
}
