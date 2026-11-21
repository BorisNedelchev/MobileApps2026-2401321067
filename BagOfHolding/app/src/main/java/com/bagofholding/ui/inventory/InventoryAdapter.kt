package com.bagofholding.ui.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bagofholding.R
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.databinding.ItemInventoryBinding
import com.bagofholding.domain.Currency
import com.bagofholding.domain.InventoryRules
import com.bagofholding.domain.WeightFormat
import com.bagofholding.ui.common.labelRes

class InventoryAdapter(
    private val onClick: (InventoryItem) -> Unit,
    private val onIncrement: (InventoryItem) -> Unit,
    private val onDecrement: (InventoryItem) -> Unit,
) : ListAdapter<InventoryItem, InventoryAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InventoryItem) {
            val context = binding.root.context
            binding.textItemName.text = item.name
            val details = buildList {
                add(context.getString(item.category.labelRes()))
                if (item.weight > 0) add(WeightFormat.format(item.weight))
                if (item.costCp > 0) add(Currency.format(item.costCp))
            }
            binding.textItemDetails.text = details.joinToString(" · ")
            binding.textItemNotes.text = item.notes
            binding.textItemNotes.isVisible = item.notes.isNotBlank()
            binding.textQuantity.text = item.quantity.toString()
            binding.buttonPlus.isEnabled = item.quantity < InventoryRules.MAX_QUANTITY
            binding.buttonMinus.contentDescription =
                context.getString(R.string.cd_remove_one, item.name)
            binding.buttonPlus.contentDescription =
                context.getString(R.string.cd_add_one, item.name)

            binding.root.setOnClickListener { onClick(item) }
            binding.buttonPlus.setOnClickListener { onIncrement(item) }
            binding.buttonMinus.setOnClickListener { onDecrement(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<InventoryItem>() {
        override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem) = oldItem == newItem
    }
}
