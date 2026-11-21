package com.bagofholding.ui.inventory

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bagofholding.R
import com.bagofholding.appContainer
import com.bagofholding.data.model.Coin
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.databinding.ActivityInventoryBinding
import com.bagofholding.databinding.ViewCoinBinding
import com.bagofholding.domain.Currency
import com.bagofholding.domain.Encumbrance
import com.bagofholding.domain.InventoryRow
import com.bagofholding.domain.InventoryStats
import com.bagofholding.domain.WeightFormat
import com.bagofholding.ui.catalog.CatalogActivity
import com.bagofholding.ui.characters.CharacterDialog
import com.bagofholding.ui.common.applySystemBarsPadding
import com.bagofholding.ui.common.collectWhileStarted
import com.bagofholding.ui.common.describe
import com.bagofholding.ui.common.dismissWith
import com.bagofholding.ui.common.labelRes
import com.bagofholding.ui.common.nameRes
import com.bagofholding.ui.share.ShareActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

/**
 * One character's inventory: carrying capacity, purse, quick +/- buttons, swipe to delete
 * (with confirmation), tap for item details, add from the database or create a custom item.
 */
class InventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventoryBinding
    private val characterId: Long by lazy { intent.getLongExtra(EXTRA_CHARACTER_ID, 0L) }
    private val viewModel: InventoryViewModel by viewModels {
        InventoryViewModel.factory(appContainer, characterId)
    }

    private val adapter = InventoryAdapter(
        onClick = { startActivity(ItemDetailActivity.intent(this, it.id)) },
        onIncrement = { viewModel.changeQuantity(it, +1) },
        onDecrement = { viewModel.changeQuantity(it, -1) },
        onToggleCollapsed = { viewModel.toggleCollapsed(it) },
    )

    /** Tapping an item in the database adds it and comes back here with the item name. */
    private val catalogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = it.data
        if (it.resultCode == RESULT_OK && data != null) {
            val name = data.getStringExtra(CatalogActivity.RESULT_ITEM_NAME).orEmpty()
            val quantity = data.getIntExtra(CatalogActivity.RESULT_QUANTITY, 1)
            Snackbar.make(binding.coordinator, getString(R.string.catalog_added, name, quantity), Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.fabAddItem)
                .show()
        }
    }

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
        dragAndSwipe().attachToRecyclerView(binding.recyclerItems)
        binding.fabAddItem.setOnClickListener { showAddChooser() }
        setUpCoins()

        collectWhileStarted(viewModel.uiState) { render(it) }
        collectWhileStarted(viewModel.events) { handle(it) }
    }

    private fun coinViews(): Map<Coin, ViewCoinBinding> = mapOf(
        Coin.PP to binding.coinPp,
        Coin.GP to binding.coinGp,
        Coin.EP to binding.coinEp,
        Coin.SP to binding.coinSp,
        Coin.CP to binding.coinCp,
    )

    private fun setUpCoins() {
        coinViews().forEach { (coin, view) ->
            view.textCoinLabel.setText(coin.labelRes())
            view.imageCoin.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, coin.colorRes()))
            view.root.setOnClickListener { showCoinDialog(coin) }
        }
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
        renderPurse(character)
        state.stats?.let { renderStats(it) }
        adapter.submit(state.rows)
        binding.textEmpty.isVisible = state.items.isEmpty()
    }

    private fun renderPurse(character: PlayerCharacter) {
        coinViews().forEach { (coin, view) ->
            val amount = character.purse[coin]
            view.textCoinAmount.text = amount.toString()
            view.root.contentDescription = getString(R.string.cd_coin, amount, getString(coin.nameRes()))
        }
        binding.textPurse.text = getString(R.string.inventory_purse, Currency.format(character.purse.totalCp))
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

    private fun showCoinDialog(coin: Coin) {
        val character = viewModel.uiState.value.character ?: return
        CoinDialog.show(this, coin, character.purse[coin]) { newAmount -> viewModel.setCoins(coin, newAmount) }
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
        val choices = arrayOf(
            getString(R.string.add_from_database),
            getString(R.string.add_custom),
            getString(R.string.add_container),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_item_title)
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> catalogLauncher.launch(CatalogActivity.intent(this, characterId))
                    1 -> showNewItemDialog()
                    else -> showNewContainerDialog()
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

    /** A storage compartment (backpack, belt…); it is added at the bottom of the list, empty. */
    private fun showNewContainerDialog() {
        val blank = InventoryItem(
            characterId = characterId,
            name = "",
            category = ItemCategory.GEAR,
            isContainer = true,
        )
        ItemDialog.show(this, blank, ItemDialog.Options.forContainer(R.string.container_new)) { item, _ ->
            viewModel.saveItem(item, alsoSaveToCatalog = false)
        }
    }

    /** Swiping asks first; "Cancel" (or dismissing the dialog) slides the row back. */
    private fun confirmSwipeDelete(row: InventoryRow) {
        var confirmed = false
        val (item, message) = when (row) {
            is InventoryRow.Container -> row.container to getString(
                R.string.container_delete_message,
                row.container.name,
            )
            is InventoryRow.Item -> {
                val stack = row.item
                val label = if (stack.quantity > 1) "${stack.quantity} × ${stack.name}" else stack.name
                stack to getString(R.string.item_delete_message, label)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (item.isContainer) R.string.container_delete_title else R.string.item_delete_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                confirmed = true
                viewModel.delete(item)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .dismissWith(this) { if (!confirmed) restoreSwipedRow(item) }
            .show()
    }

    private fun restoreSwipedRow(item: InventoryItem) {
        val index = adapter.positionOf(item.id)
        if (index >= 0) adapter.notifyItemChanged(index)
    }

    /**
     * Long press + drag moves a row (dropping an item under a compartment puts it inside;
     * a collapsed compartment takes its hidden contents along). Swipe left/right deletes.
     */
    private fun dragAndSwipe() = ItemTouchHelper(
        object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        ) {
            private var moved = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                adapter.move(from, to)
                moved = true
                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    moved = false
                    adapter.startDrag()
                    viewHolder?.itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (adapter.isDragging) {
                    val order = adapter.endDrag(moved)
                    if (moved) viewModel.reorder(order)
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) confirmSwipeDelete(adapter.rowAt(position))
            }
        },
    )

    companion object {
        private const val EXTRA_CHARACTER_ID = "character_id"

        fun intent(context: Context, characterId: Long): Intent =
            Intent(context, InventoryActivity::class.java).putExtra(EXTRA_CHARACTER_ID, characterId)
    }
}

private fun Coin.colorRes(): Int = when (this) {
    Coin.PP -> R.color.coin_pp
    Coin.GP -> R.color.coin_gp
    Coin.EP -> R.color.coin_ep
    Coin.SP -> R.color.coin_sp
    Coin.CP -> R.color.coin_cp
}
