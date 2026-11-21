package com.bagofholding.ui.inventory

import android.widget.ArrayAdapter
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bagofholding.R
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.databinding.DialogItemBinding
import com.bagofholding.domain.Currency
import com.bagofholding.domain.FormResult
import com.bagofholding.domain.ItemField
import com.bagofholding.domain.ItemInput
import com.bagofholding.domain.ItemValidator
import com.bagofholding.domain.WeightFormat
import com.bagofholding.ui.common.dismissWith
import com.bagofholding.ui.common.labelRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Form for a custom item. Used for inventory items (with quantity and an optional
 * "also save to the item database" checkbox) and for custom catalog entries.
 */
object ItemDialog {

    data class Options(
        @StringRes val title: Int,
        val showQuantity: Boolean = true,
        val showSaveToCatalog: Boolean = false,
    )

    fun show(
        activity: AppCompatActivity,
        base: InventoryItem,
        options: Options,
        onSave: (item: InventoryItem, saveToCatalog: Boolean) -> Unit,
    ) {
        val binding = DialogItemBinding.inflate(activity.layoutInflater)
        val categories = ItemCategory.entries
        val labels = categories.map { activity.getString(it.labelRes()) }
        var selectedCategory = base.category

        binding.editCategory.setAdapter(ArrayAdapter(activity, R.layout.item_dropdown, labels))
        binding.editCategory.setText(activity.getString(base.category.labelRes()), false)
        binding.editCategory.setOnItemClickListener { _, _, position, _ -> selectedCategory = categories[position] }

        binding.editItemName.setText(base.name)
        binding.editQuantity.setText(base.quantity.coerceAtLeast(1).toString())
        binding.editWeight.setText(if (base.weight == 0.0) "" else WeightFormat.format(base.weight).removeSuffix(" lb"))
        binding.editCost.setText(if (base.costCp == 0L) "" else Currency.toGoldInput(base.costCp))
        binding.editNotes.setText(base.notes)
        binding.inputQuantity.isVisible = options.showQuantity
        binding.checkSaveToCatalog.isVisible = options.showSaveToCatalog

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(options.title)
            .setView(binding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .dismissWith(activity)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val input = ItemInput(
                    name = binding.editItemName.text.toString(),
                    category = selectedCategory,
                    quantity = binding.editQuantity.text.toString(),
                    weight = binding.editWeight.text.toString(),
                    costGold = binding.editCost.text.toString(),
                    notes = binding.editNotes.text.toString(),
                )
                when (val result = ItemValidator.validate(base, input)) {
                    is FormResult.Valid -> {
                        onSave(result.value, binding.checkSaveToCatalog.isChecked)
                        dialog.dismiss()
                    }
                    is FormResult.Invalid -> showErrors(binding, result.errors)
                }
            }
        }
        dialog.show()
    }

    private fun showErrors(binding: DialogItemBinding, errors: Set<ItemField>) {
        val context = binding.root.context
        fun message(field: ItemField, res: Int) = if (field in errors) context.getString(res) else null
        binding.inputItemName.error = message(ItemField.NAME, R.string.error_item_name)
        binding.inputQuantity.error = message(ItemField.QUANTITY, R.string.error_quantity)
        binding.inputWeight.error = message(ItemField.WEIGHT, R.string.error_weight)
        binding.inputCost.error = message(ItemField.COST, R.string.error_cost)
    }
}
