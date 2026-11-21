package com.bagofholding.ui.inventory

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bagofholding.R
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.databinding.ItemContainerBinding
import com.bagofholding.databinding.ItemInventoryBinding
import com.bagofholding.domain.Currency
import com.bagofholding.domain.InventoryRow
import com.bagofholding.domain.InventoryRules
import com.bagofholding.domain.ValueTint
import com.bagofholding.domain.WeightFormat
import com.bagofholding.ui.common.labelRes

/**
 * Inventory list with two row types: items and storage compartments. Rows can be dragged
 * (long press) — while a drag is running the adapter owns the order and ignores new data,
 * see [startDrag] / [move] / [endDrag].
 */
class InventoryAdapter(
    private val onClick: (InventoryItem) -> Unit,
    private val onIncrement: (InventoryItem) -> Unit,
    private val onDecrement: (InventoryItem) -> Unit,
    private val onToggleCollapsed: (InventoryItem) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<InventoryRow>()
    private var pending: List<InventoryRow>? = null

    var isDragging: Boolean = false
        private set

    fun submit(newRows: List<InventoryRow>) {
        if (isDragging) {
            pending = newRows
            return
        }
        val diff = DiffUtil.calculateDiff(RowDiff(rows.toList(), newRows))
        rows.clear()
        rows.addAll(newRows)
        diff.dispatchUpdatesTo(this)
    }

    fun rowAt(position: Int): InventoryRow = rows[position]

    fun positionOf(id: Long): Int = rows.indexOfFirst { it.id == id }

    fun startDrag() {
        isDragging = true
        pending = null
    }

    fun move(from: Int, to: Int) {
        rows.add(to, rows.removeAt(from))
        notifyItemMoved(from, to)
    }

    /**
     * Ends the drag and returns the new on-screen order. If nothing moved, data that arrived
     * during the drag is shown now; otherwise the saved order will arrive from the database.
     */
    fun endDrag(moved: Boolean): List<Long> {
        isDragging = false
        val waiting = pending
        pending = null
        if (!moved && waiting != null) submit(waiting)
        return rows.map { it.id }
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is InventoryRow.Item -> TYPE_ITEM
        is InventoryRow.Container -> TYPE_CONTAINER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CONTAINER) {
            ContainerViewHolder(ItemContainerBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemInventoryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is InventoryRow.Item -> (holder as ItemViewHolder).bind(row)
            is InventoryRow.Container -> (holder as ContainerViewHolder).bind(row)
        }
    }

    inner class ItemViewHolder(private val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root) {
        private val defaultNameColors: ColorStateList = binding.textItemName.textColors

        fun bind(row: InventoryRow.Item) {
            val item = row.item
            val context = binding.root.context
            val indent = context.resources.getDimensionPixelSize(R.dimen.content_indent)
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = if (row.inContainer) indent else 0
            }
            binding.textItemName.text = item.name
            binding.textItemName.applyMagicColor(item.category, defaultNameColors)

            // The more the stack is worth, the more golden the row (up to ValueTint.FULL_GOLD_GP).
            val base = ContextCompat.getColor(context, R.color.boh_surface_container_highest)
            val gold = ContextCompat.getColor(context, R.color.value_gold)
            val fraction = ValueTint.goldFraction(item.costCp * item.quantity)
            binding.root.setCardBackgroundColor(ColorUtils.blendARGB(base, gold, fraction))

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
            binding.buttonMinus.contentDescription = context.getString(R.string.cd_remove_one, item.name)
            binding.buttonPlus.contentDescription = context.getString(R.string.cd_add_one, item.name)

            binding.root.setOnClickListener { onClick(item) }
            binding.buttonPlus.setOnClickListener { onIncrement(item) }
            binding.buttonMinus.setOnClickListener { onDecrement(item) }
        }
    }

    inner class ContainerViewHolder(private val binding: ItemContainerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val defaultNameColors: ColorStateList = binding.textContainerName.textColors
        private val defaultDetailColors: ColorStateList = binding.textContainerDetails.textColors

        fun bind(row: InventoryRow.Container) {
            val container = row.container
            val section = row.section
            val context = binding.root.context
            binding.textContainerName.text = container.name
            binding.textContainerName.applyMagicColor(container.category, defaultNameColors)

            val count = section.items.sumOf { it.quantity }
            val load = if (container.capacity > 0) {
                context.getString(
                    R.string.container_load,
                    WeightFormat.format(section.contentsWeight),
                    WeightFormat.format(container.capacity),
                )
            } else {
                WeightFormat.format(section.contentsWeight)
            }
            val parts = buildList {
                add(context.resources.getQuantityString(R.plurals.container_items, count, count))
                add(load)
                if (container.weightlessContents) add(context.getString(R.string.container_weightless_short))
            }
            binding.textContainerDetails.text = parts.joinToString(" · ")
            val overloaded = container.capacity > 0 && section.contentsWeight > container.capacity
            if (overloaded) {
                binding.textContainerDetails.setTextColor(ContextCompat.getColor(context, R.color.boh_error))
            } else {
                binding.textContainerDetails.setTextColor(defaultDetailColors)
            }

            binding.buttonCollapse.setIconResource(
                if (container.collapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less,
            )
            binding.buttonCollapse.contentDescription = context.getString(
                if (container.collapsed) R.string.cd_show_contents else R.string.cd_hide_contents,
                container.name,
            )
            binding.buttonCollapse.setOnClickListener { onToggleCollapsed(container) }
            binding.root.setOnClickListener { onClick(container) }
        }
    }

    private class RowDiff(
        private val old: List<InventoryRow>,
        private val new: List<InventoryRow>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
            val a = old[oldPosition]
            val b = new[newPosition]
            return a.id == b.id && a::class == b::class
        }

        override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition] == new[newPosition]
    }

    private companion object {
        const val TYPE_ITEM = 0
        const val TYPE_CONTAINER = 1
    }
}

/** Magic items are written in blue (also used on the details and database screens). */
fun TextView.applyMagicColor(category: ItemCategory, defaultColors: ColorStateList) {
    if (category == ItemCategory.MAGIC) {
        setTextColor(ContextCompat.getColor(context, R.color.magic_blue))
    } else {
        setTextColor(defaultColors)
    }
}
