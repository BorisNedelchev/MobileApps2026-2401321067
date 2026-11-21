package com.bagofholding.ui.share

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bagofholding.R
import com.bagofholding.appContainer
import com.bagofholding.databinding.ActivityShareBinding
import com.bagofholding.domain.WeightFormat
import com.bagofholding.network.SharePayload
import com.bagofholding.ui.common.applySystemBarsPadding
import com.bagofholding.ui.common.collectWhileStarted
import com.bagofholding.ui.common.dismissWith
import com.bagofholding.ui.inventory.InventoryActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * LAN sharing. "Share" starts a small HTTP server on this phone and advertises it with NSD;
 * "Receive" lists phones found on the same Wi-Fi (or takes a typed IP address) and downloads
 * their inventory, which can be imported as a new character or merged into the current one.
 */
class ShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding
    private val characterId: Long by lazy { intent.getLongExtra(EXTRA_CHARACTER_ID, 0L) }
    private val viewModel: ShareViewModel by viewModels { ShareViewModel.factory(appContainer, characterId) }
    private val hostAdapter = HostAdapter { viewModel.fetch(it.address) }
    private var receivedDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.recyclerHosts.adapter = hostAdapter
        binding.buttonHost.setOnClickListener {
            if (viewModel.uiState.value.isHosting) viewModel.stopHosting() else viewModel.startHosting()
        }
        binding.buttonFetch.setOnClickListener { fetchTypedAddress() }
        binding.editAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                fetchTypedAddress()
                true
            } else {
                false
            }
        }

        collectWhileStarted(viewModel.uiState) { render(it) }
        collectWhileStarted(viewModel.events) { handle(it) }
    }

    private fun fetchTypedAddress() {
        viewModel.fetchFromText(binding.editAddress.text?.toString().orEmpty())
    }

    private fun render(state: ShareUiState) {
        binding.cardHost.isVisible = state.canHost
        binding.textHostTitle.text = getString(R.string.share_host_title, state.characterName)
        binding.buttonHost.setText(if (state.isHosting) R.string.share_stop else R.string.share_start)
        binding.textHostAddress.isVisible = state.isHosting
        binding.textHostAddress.text = if (state.hostAddresses.isEmpty()) {
            getString(R.string.share_no_wifi, state.port)
        } else {
            getString(
                R.string.share_hosting_at,
                state.hostAddresses.joinToString("\n") { "$it:${state.port}" },
            )
        }

        hostAdapter.submitList(state.discoveredHosts)
        binding.textNoHosts.isVisible = state.discoveredHosts.isEmpty()
        binding.progressFetch.isVisible = state.isFetching
        binding.buttonFetch.isEnabled = !state.isFetching

        val received = state.received
        if (received != null && receivedDialog == null) showReceivedDialog(received, state)
    }

    private fun showReceivedDialog(payload: SharePayload, state: ShareUiState) {
        val totalWeight = payload.items.sumOf { it.weight * it.quantity }
        val itemCount = payload.items.sumOf { it.quantity }
        val message = resources.getQuantityString(
            R.plurals.share_received_message,
            itemCount,
            payload.character.name,
            itemCount,
            WeightFormat.format(totalWeight),
        )
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_received_title)
            .setMessage(message)
            .setPositiveButton(R.string.share_import_new) { _, _ -> viewModel.importAsNewCharacter() }
            .setNegativeButton(R.string.action_cancel) { _, _ -> viewModel.dismissReceived() }
            .setOnCancelListener { viewModel.dismissReceived() }
        if (state.canHost) {
            builder.setNeutralButton(getString(R.string.share_merge_into, state.characterName)) { _, _ ->
                viewModel.mergeIntoCurrentCharacter()
            }
        }
        val dialog = builder.create().dismissWith(this) { receivedDialog = null }
        receivedDialog = dialog
        dialog.show()
    }

    private fun handle(event: ShareEvent) {
        when (event) {
            is ShareEvent.Error -> {
                val message = when (event.error) {
                    ShareError.INVALID_ADDRESS -> R.string.share_error_address
                    ShareError.CONNECTION_FAILED -> R.string.share_error_connection
                    ShareError.BAD_DATA -> R.string.share_error_data
                    ShareError.HOST_FAILED -> R.string.share_error_host
                }
                Snackbar.make(binding.coordinator, message, Snackbar.LENGTH_LONG).show()
            }
            is ShareEvent.Imported -> {
                if (event.merged) {
                    Snackbar.make(
                        binding.coordinator,
                        getString(R.string.share_merged, event.characterName),
                        Snackbar.LENGTH_LONG,
                    ).show()
                } else {
                    Snackbar.make(
                        binding.coordinator,
                        getString(R.string.share_imported, event.characterName),
                        Snackbar.LENGTH_LONG,
                    ).setAction(R.string.action_open) {
                        startActivity(InventoryActivity.intent(this, event.characterId))
                    }.show()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CHARACTER_ID = "character_id"

        /** [characterId] = 0 opens the screen in receive-only mode. */
        fun intent(context: Context, characterId: Long): Intent =
            Intent(context, ShareActivity::class.java).putExtra(EXTRA_CHARACTER_ID, characterId)
    }
}
