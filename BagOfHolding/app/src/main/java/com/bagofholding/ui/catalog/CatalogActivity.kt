package com.bagofholding.ui.catalog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.bagofholding.R
import com.bagofholding.appContainer
import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.toCatalogItem
import com.bagofholding.databinding.ActivityCatalogBinding
import com.bagofholding.databinding.ItemFilterChipBinding
import com.bagofholding.ui.common.applySystemBarsPadding
import com.bagofholding.ui.common.collectWhileStarted
import com.bagofholding.ui.common.dismissWith
import com.bagofholding.ui.common.labelRes
import com.bagofholding.ui.inventory.ItemDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Built-in item database plus the player's custom items. Tap an item to add it to the character. */
class CatalogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCatalogBinding
    private val characterId: Long by lazy { intent.getLongExtra(EXTRA_CHARACTER_ID, 0L) }
    private val viewModel: CatalogViewModel by viewModels { CatalogViewModel.factory(appContainer, characterId) }
    private lateinit var adapter: CatalogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_new_catalog_item) {
                showNewCustomItemDialog()
                true
            } else {
                false
            }
        }

        adapter = CatalogAdapter(
            canAdd = viewModel.canAddToInventory,
            onAdd = { viewModel.addToInventory(it) },
            onLongClick = { onItemLongClick(it) },
        )
        binding.recyclerCatalog.adapter = adapter
        binding.editSearch.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        setUpCategoryChips()

        collectWhileStarted(viewModel.items) { items ->
            binding.progressLoading.isVisible = items == null
            if (items != null) {
                adapter.submitList(items)
                binding.textEmpty.isVisible = items.isEmpty()
            }
        }
        collectWhileStarted(viewModel.events) { handle(it) }
    }

    private fun setUpCategoryChips() {
        val options = listOf<ItemCategory?>(null) + ItemCategory.entries
        val selected = viewModel.category.value
        options.forEach { category ->
            val chip = ItemFilterChipBinding.inflate(layoutInflater, binding.chipGroupCategories, false).root
            chip.id = View.generateViewId()
            chip.text = if (category == null) getString(R.string.category_all) else getString(category.labelRes())
            chip.tag = category
            chip.isChecked = category == selected
            binding.chipGroupCategories.addView(chip)
        }
        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<View>(it) }
            viewModel.setCategory(chip?.tag as? ItemCategory)
        }
    }

    private fun handle(event: CatalogEvent) {
        val message = when (event) {
            is CatalogEvent.AddedToInventory ->
                getString(R.string.catalog_added, event.item.name, event.item.quantity)
            is CatalogEvent.Deleted -> getString(R.string.catalog_deleted, event.item.name)
        }
        Snackbar.make(binding.coordinator, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun onItemLongClick(item: CatalogItem) {
        if (!item.isCustom) {
            Snackbar.make(binding.coordinator, R.string.catalog_builtin_protected, Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(arrayOf(getString(R.string.action_edit), getString(R.string.action_delete))) { _, which ->
                if (which == 0) showEditCustomItemDialog(item) else viewModel.deleteCustom(item)
            }
            .create()
            .dismissWith(this)
            .show()
    }

    private fun showNewCustomItemDialog() {
        val blank = InventoryItem(characterId = 0, name = "", category = ItemCategory.GEAR)
        ItemDialog.show(this, blank, ItemDialog.Options(R.string.catalog_new_item, showQuantity = false)) { item, _ ->
            viewModel.saveCustom(item.toCatalogItem())
        }
    }

    private fun showEditCustomItemDialog(catalogItem: CatalogItem) {
        val asItem = InventoryItem(
            characterId = 0,
            name = catalogItem.name,
            category = catalogItem.category,
            weight = catalogItem.weight,
            costCp = catalogItem.costCp,
            notes = catalogItem.description,
        )
        ItemDialog.show(this, asItem, ItemDialog.Options(R.string.catalog_edit_item, showQuantity = false)) { item, _ ->
            viewModel.saveCustom(item.toCatalogItem().copy(id = catalogItem.id))
        }
    }

    companion object {
        private const val EXTRA_CHARACTER_ID = "character_id"

        fun intent(context: Context, characterId: Long): Intent =
            Intent(context, CatalogActivity::class.java).putExtra(EXTRA_CHARACTER_ID, characterId)
    }
}
