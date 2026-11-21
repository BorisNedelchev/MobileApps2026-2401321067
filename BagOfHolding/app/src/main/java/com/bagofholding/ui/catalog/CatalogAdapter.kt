package com.bagofholding.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bagofholding.R
import com.bagofholding.data.model.CatalogItem
import com.bagofholding.databinding.ItemCatalogBinding
import com.bagofholding.domain.Currency
import com.bagofholding.domain.WeightFormat
import com.bagofholding.ui.common.labelRes

class CatalogAdapter(
    private val canAdd: Boolean,
    private val onAdd: (CatalogItem) -> Unit,
    private val onLongClick: (CatalogItem) -> Unit,
) : ListAdapter<CatalogItem, CatalogAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemCatalogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemCatalogBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CatalogItem) {
            val context = binding.root.context
            binding.textCatalogName.text = item.name
            binding.textCustomBadge.isVisible = item.isCustom
            val details = buildList {
                add(context.getString(item.category.labelRes()))
                add(WeightFormat.format(item.weight))
                add(if (item.costCp > 0) Currency.format(item.costCp) else context.getString(R.string.catalog_no_price))
            }
            binding.textCatalogDetails.text = details.joinToString(" · ")
            binding.textCatalogDescription.text = item.description
            binding.textCatalogDescription.isVisible = item.description.isNotBlank()

            binding.buttonAdd.isVisible = canAdd
            binding.buttonAdd.contentDescription = context.getString(R.string.cd_add_one, item.name)
            binding.buttonAdd.setOnClickListener { onAdd(item) }
            binding.root.setOnClickListener { if (canAdd) onAdd(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CatalogItem>() {
        override fun areItemsTheSame(oldItem: CatalogItem, newItem: CatalogItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CatalogItem, newItem: CatalogItem) = oldItem == newItem
    }
}
