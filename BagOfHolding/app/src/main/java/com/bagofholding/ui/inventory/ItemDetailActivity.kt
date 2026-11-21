package com.bagofholding.ui.inventory

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bagofholding.R
import com.bagofholding.appContainer
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.databinding.ActivityItemDetailBinding
import com.bagofholding.domain.Currency
import com.bagofholding.domain.WeightFormat
import com.bagofholding.ui.common.applySystemBarsPadding
import com.bagofholding.ui.common.collectWhileStarted
import com.bagofholding.ui.common.dismissWith
import com.bagofholding.ui.common.labelRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tapping an item in the inventory opens this description page first.
 * The feather button (bottom right) opens the edit form.
 */
class ItemDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityItemDetailBinding
    private lateinit var defaultNameColors: ColorStateList
    private val itemId: Long by lazy { intent.getLongExtra(EXTRA_ITEM_ID, 0L) }
    private val viewModel: ItemDetailViewModel by viewModels { ItemDetailViewModel.factory(appContainer, itemId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (itemId <= 0L) {
            finish()
            return
        }
        binding = ActivityItemDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        defaultNameColors = binding.textDetailName.textColors
        binding.root.applySystemBarsPadding()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_delete_item) {
                viewModel.uiState.value.item?.let { confirmDelete(it) }
                true
            } else {
                false
            }
        }
        binding.fabEdit.setOnClickListener { viewModel.uiState.value.item?.let { showEditDialog(it) } }

        collectWhileStarted(viewModel.uiState) { render(it) }
    }

    private fun render(state: ItemDetailUiState) {
        binding.progressLoading.isVisible = state.isLoading
        binding.scroll.isVisible = !state.isLoading
        if (state.isLoading) return
        val item = state.item
        if (item == null) {
            finish() // Deleted.
            return
        }
        binding.toolbar.title = item.name
        binding.textDetailName.text = item.name
        binding.textDetailName.applyMagicColor(item.category, defaultNameColors)
        binding.textDetailCategory.text = if (item.isContainer) {
            getString(R.string.container_label)
        } else {
            getString(item.category.labelRes())
        }
        binding.rowQuantity.isVisible = !item.isContainer
        binding.textDetailQuantity.text = item.quantity.toString()
        renderContainer(item, state.contents)
        binding.textDetailWeight.text = getString(
            R.string.detail_each_total,
            WeightFormat.format(item.weight),
            WeightFormat.format(item.weight * item.quantity),
        )
        binding.textDetailValue.text = if (item.costCp > 0) {
            getString(
                R.string.detail_each_total,
                Currency.format(item.costCp),
                Currency.format(item.costCp * item.quantity),
            )
        } else {
            getString(R.string.catalog_no_price)
        }
        binding.textDetailDescription.text = item.description.ifBlank { getString(R.string.detail_no_description) }
        binding.textDetailDescription.setTypeface(
            null,
            if (item.description.isBlank()) Typeface.ITALIC else Typeface.NORMAL,
        )
        binding.groupNotes.isVisible = item.notes.isNotBlank()
        binding.textDetailNotes.text = item.notes
    }

    /** Storage compartments show how full they are and what is inside. */
    private fun renderContainer(item: InventoryItem, contents: List<InventoryItem>) {
        binding.rowCapacity.isVisible = item.isContainer
        binding.groupContents.isVisible = item.isContainer
        if (!item.isContainer) return
        val contentsWeight = contents.sumOf { it.weight * it.quantity }
        val load = if (item.capacity > 0) {
            getString(R.string.container_load, WeightFormat.format(contentsWeight), WeightFormat.format(item.capacity))
        } else {
            getString(R.string.container_no_limit, WeightFormat.format(contentsWeight))
        }
        binding.textDetailCapacity.text = if (item.weightlessContents) {
            getString(R.string.container_load_weightless, load)
        } else {
            load
        }
        binding.textDetailContents.text = if (contents.isEmpty()) {
            getString(R.string.container_empty)
        } else {
            contents.joinToString("\n") { "• ${it.name} ×${it.quantity}" }
        }
    }

    private fun showEditDialog(item: InventoryItem) {
        val options = if (item.isContainer) {
            ItemDialog.Options.forContainer(R.string.container_edit)
        } else {
            ItemDialog.Options(title = R.string.item_edit)
        }
        ItemDialog.show(this, item, options) { edited, _ -> viewModel.save(edited) }
    }

    private fun confirmDelete(item: InventoryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(if (item.isContainer) R.string.container_delete_title else R.string.item_delete_title)
            .setMessage(
                getString(
                    if (item.isContainer) R.string.container_delete_message else R.string.item_delete_message,
                    item.name,
                ),
            )
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .dismissWith(this)
            .show()
    }

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"

        fun intent(context: Context, itemId: Long): Intent =
            Intent(context, ItemDetailActivity::class.java).putExtra(EXTRA_ITEM_ID, itemId)
    }
}
