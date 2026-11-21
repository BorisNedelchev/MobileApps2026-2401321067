package com.bagofholding.ui.inventory

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bagofholding.R
import com.bagofholding.appContainer
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.databinding.ActivityInventoryBinding
import com.bagofholding.domain.Currency
import com.bagofholding.domain.Encumbrance
import com.bagofholding.domain.InventoryStats
import com.bagofholding.domain.WeightFormat
import com.bagofholding.ui.catalog.CatalogActivity
import com.bagofholding.ui.characters.CharacterDialog
import com.bagofholding.ui.common.applySystemBarsPadding
import com.bagofholding.ui.common.collectWhileStarted
import com.bagofholding.ui.common.describe
import com.bagofholding.ui.common.dismissWith
import com.bagofholding.ui.common.labelRes
import com.bagofholding.ui.share.ShareActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

/** One character's inventory: quick +/- buttons, swipe to delete, add from database or custom. */
class InventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventoryBinding
    private val characterId: Long by lazy { intent.getLongExtra(EXTRA_CHARACTER_ID, 0L) }
    private val viewModel: InventoryViewModel by viewModels {
        InventoryViewModel.factory(appContainer, characterId)
    }

    private val adapter = InventoryAdapter(
        onClick = { showEditItemDialog(it) },
        onIncrement = { viewModel.changeQuantity(it, +1) },
        onDecrement = { viewModel.changeQuantity(it, -1) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (characterId <= 0L) {
            finish()
            return
        }
        binding = ActivityInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener(::onMenuItemSelected)
        binding.recyclerItems.adapter = adapter
        swipeToDelete().attachToRecyclerView(binding.recyclerItems)
        binding.fabAddItem.setOnClickListener { showAddChooser() }

        collectWhileStarted(viewModel.uiState) { render(it) }
        collectWhileStarted(viewModel.events) { handle(it) }
    }

    private fun render(state: InventoryUiState) {
        binding.progressLoading.isVisible = state.isLoading
        if (state.isLoading) return
        val character = state.character
        if (character == null) {
            finish() // The character was deleted.
            return
        }
        binding.toolbar.title = character.name
        binding.toolbar.subtitle = character.describe(this)
        binding.textPurse.text = getString(R.string.inventory_purse, Currency.format(character.purseCp))
        state.stats?.let { renderStats(it) }
        adapter.submitList(state.items)
        binding.textEmpty.isVisible = state.items.isEmpty()
    }

    private fun renderStats(stats: InventoryStats) {
        binding.textWeight.text = getString(
            R.string.inventory_weight,
            WeightFormat.format(stats.totalWeight),
            WeightFormat.format(stats.carryingCapacity),
        )
        binding.textValue.text = getString(R.string.inventory_value, Currency.format(stats.totalValueCp))
        binding.textEncumbrance.setText(stats.encumbrance.labelRes())

        val percent = if (stats.carryingCapacity > 0) {
            (stats.totalWeight / stats.carryingCapacity * 100).roundToInt().coerceIn(0, 100)
        } else {
            100
        }
        binding.progressWeight.setProgressCompat(percent, true)
        val warn = stats.encumbrance != Encumbrance.UNENCUMBERED
        // Our own color resources have light and night variants, so this follows the theme.
        val color = ContextCompat.getColor(this, if (warn) R.color.boh_error else R.color.boh_primary)
        binding.progressWeight.setIndicatorColor(color)
        binding.textEncumbrance.setTextColor(color)
    }

    private fun handle(event: InventoryEvent) {
        when (event) {
            is InventoryEvent.ItemRemoved ->
                Snackbar.make(
                    binding.coordinator,
                    getString(R.string.item_removed, event.item.name),
                    Snackbar.LENGTH_LONG,
                ).setAction(R.string.action_undo) { viewModel.restore(event.item) }
                    .setAnchorView(binding.fabAddItem)
                    .show()
        }
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_share_wifi -> startActivity(ShareActivity.intent(this, characterId))
            R.id.action_share_text -> shareAsText()
            R.id.action_edit_character -> viewModel.uiState.value.character?.let { character ->
                CharacterDialog.show(this, character) { viewModel.saveCharacter(it) }
            }
            else -> return false
        }
        return true
    }

    /** Share Intent: sends the inventory as plain text to any app (chat, notes, e-mail…). */
    private fun shareAsText() {
        val text = viewModel.shareText() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, binding.toolbar.title))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.menu_share_text)))
    }

    private fun showAddChooser() {
        val choices = arrayOf(getString(R.string.add_from_database), getString(R.string.add_custom))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_item_title)
            .setItems(choices) { _, which ->
                if (which == 0) {
                    startActivity(CatalogActivity.intent(this, characterId))
                } else {
                    showNewItemDialog()
                }
            }
            .create()
            .dismissWith(this)
            .show()
    }

    private fun showNewItemDialog() {
        val blank = InventoryItem(characterId = characterId, name = "", category = ItemCategory.GEAR)
        ItemDialog.show(
            this,
            blank,
            ItemDialog.Options(title = R.string.item_new, showSaveToCatalog = true),
        ) { item, saveToCatalog -> viewModel.saveItem(item, saveToCatalog) }
    }

    private fun showEditItemDialog(item: InventoryItem) {
        ItemDialog.show(this, item, ItemDialog.Options(title = R.string.item_edit)) { edited, _ ->
            viewModel.saveItem(edited, alsoSaveToCatalog = false)
        }
    }

    private fun swipeToDelete() = ItemTouchHelper(
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) viewModel.delete(adapter.currentList[position])
            }
        },
    )

    companion object {
        private const val EXTRA_CHARACTER_ID = "character_id"

        fun intent(context: Context, characterId: Long): Intent =
            Intent(context, InventoryActivity::class.java).putExtra(EXTRA_CHARACTER_ID, characterId)
    }
}
