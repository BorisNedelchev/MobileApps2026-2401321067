package com.bagofholding.domain

import com.bagofholding.data.model.InventoryItem

/**
 * A storage compartment (or the loose items at the top when [container] is null) and the items
 * inside it.
 */
data class InventorySection(val container: InventoryItem?, val items: List<InventoryItem>) {
    /** Weight of the contents only (not of the compartment itself). */
    val contentsWeight: Double get() = items.sumOf { it.weight * it.quantity }
}

/** One row of the inventory list. */
sealed interface InventoryRow {
    val id: Long

    data class Item(val item: InventoryItem, val inContainer: Boolean) : InventoryRow {
        override val id: Long get() = item.id
    }

    data class Container(val section: InventorySection) : InventoryRow {
        val container: InventoryItem get() = checkNotNull(section.container)
        override val id: Long get() = container.id
    }
}

/**
 * Storage compartments work like section headers: items are kept in one ordered list and every
 * item below a compartment, up to the next compartment, is inside it. Items above the first
 * compartment are carried loose. Dragging an item under a backpack therefore puts it in the
 * backpack, and dragging it to the top takes it out again.
 */
object InventoryLayout {

    /** Screen order: by sortOrder, ties by id (stable). */
    fun ordered(items: List<InventoryItem>): List<InventoryItem> =
        items.sortedWith(compareBy<InventoryItem>({ it.sortOrder }, { it.id }))

    /** Always starts with the loose section (container == null), which may be empty. */
    fun sections(items: List<InventoryItem>): List<InventorySection> {
        val result = mutableListOf<InventorySection>()
        var container: InventoryItem? = null
        var current = mutableListOf<InventoryItem>()
        ordered(items).forEach { item ->
            if (item.isContainer) {
                result += InventorySection(container, current)
                container = item
                current = mutableListOf()
            } else {
                current += item
            }
        }
        result += InventorySection(container, current)
        return result
    }

    /** Rows to show: contents of collapsed compartments are hidden behind them. */
    fun rows(items: List<InventoryItem>): List<InventoryRow> = sections(items).flatMap { section ->
        val container = section.container
        when {
            container == null -> section.items.map { InventoryRow.Item(it, inContainer = false) }
            container.collapsed -> listOf(InventoryRow.Container(section))
            else -> listOf(InventoryRow.Container(section)) + section.items.map { InventoryRow.Item(it, true) }
        }
    }

    fun contentsOf(items: List<InventoryItem>, containerId: Long): List<InventoryItem> =
        sections(items).firstOrNull { it.container?.id == containerId }?.items.orEmpty()

    /**
     * Weight that counts toward carrying capacity: every item, except the contents of
     * compartments with weightless contents (Bag of Holding).
     */
    fun carriedWeight(items: List<InventoryItem>): Double = sections(items).sumOf { section ->
        val container = section.container
        val own = container?.let { it.weight * it.quantity } ?: 0.0
        val contents = if (container?.weightlessContents == true) 0.0 else section.contentsWeight
        own + contents
    }

    /**
     * Applies a drag & drop result. [visibleOrder] are the ids of the visible rows in their new
     * order. Contents hidden behind a collapsed compartment travel with it. Returns every item
     * with a fresh sortOrder (0, 1, 2…).
     */
    fun reorder(items: List<InventoryItem>, visibleOrder: List<Long>): List<InventoryItem> {
        val byId = items.associateBy { it.id }
        val hidden = sections(items)
            .mapNotNull { section -> section.container?.takeIf { it.collapsed }?.let { it.id to section.items } }
            .toMap()
        val placed = LinkedHashSet<Long>()
        val result = mutableListOf<InventoryItem>()
        fun place(item: InventoryItem) {
            if (placed.add(item.id)) result += item
        }
        visibleOrder.mapNotNull { byId[it] }.forEach { item ->
            place(item)
            hidden[item.id]?.forEach { place(it) }
        }
        ordered(items).forEach { place(it) } // anything not on screen keeps its relative order at the end
        return result.mapIndexed { index, item -> item.copy(sortOrder = index) }
    }

    /**
     * Deleting a compartment must not move its contents into the compartment above it, so the
     * contents are moved to the top (loose). Returns the contents with their new sortOrder.
     */
    fun releaseContents(items: List<InventoryItem>, containerId: Long): List<InventoryItem> {
        val contents = contentsOf(items, containerId)
        val top = items.minOfOrNull { it.sortOrder } ?: 0
        return contents.mapIndexed { index, item -> item.copy(sortOrder = top - contents.size + index) }
    }

    /** sortOrder for a new item: loose items go to the top, new compartments to the bottom. */
    fun sortOrderForNew(existing: List<InventoryItem>, isContainer: Boolean): Int = if (isContainer) {
        (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
    } else {
        (existing.minOfOrNull { it.sortOrder } ?: 1) - 1
    }
}

/** How "golden" an item row looks: 0 = plain, 1 = full gold. Logarithmic, so cheap items stay plain. */
object ValueTint {
    /** At or below this the row is plain. */
    const val PLAIN_UP_TO_GP = 1.0

    /** At or above this the row is fully golden. */
    const val FULL_GOLD_GP = 500.0

    fun goldFraction(valueCp: Long): Float {
        val gp = valueCp / Currency.CP_PER_GP.toDouble()
        if (gp <= PLAIN_UP_TO_GP) return 0f
        val fraction = Math.log10(gp / PLAIN_UP_TO_GP) / Math.log10(FULL_GOLD_GP / PLAIN_UP_TO_GP)
        return fraction.coerceIn(0.0, 1.0).toFloat()
    }
}
