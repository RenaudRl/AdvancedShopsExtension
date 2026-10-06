package com.btc.shops.service

/**
 * Decides where the items of a shop land in its content area.
 *
 * An item that names a slot ([plan]'s `slotOf`) is pinned there, on every page. Every other item
 * flows through the SHOP_ITEM markers of the layout, page after page, in declaration order. A
 * marker claimed by a pinned item is no longer available to the flow: two items never share a cell.
 */
object ItemPlacement {

    /** A cell of the content area, in its own coordinates (column [x], row [y]). */
    data class Cell(val x: Int, val y: Int)

    data class Placed<T>(val item: T, val cell: Cell)

    /** What a shop displays: pinned items, plus the flowing ones spread over the free markers. */
    class PagePlan<T>(
        val pinned: List<Placed<T>>,
        private val flowing: List<T>,
        private val flowCells: List<Cell>,
        /** Items that fit nowhere: no marker and no free cell. The caller must report them. */
        val unplaced: List<T> = emptyList(),
        /** True when the flowing items use the free cells of the area because no marker was left. */
        val usedFreeCells: Boolean = false,
    ) {
        /** Number of flowing items one page holds. */
        val pageSize: Int get() = flowCells.size

        /** Index of the last page; 0 when nothing flows. */
        val lastPage: Int get() = if (pageSize > 0) ((flowing.size - 1) / pageSize).coerceAtLeast(0) else 0

        /** Every item shown on [page], with its cell: pinned items first, then the flowing ones. */
        fun itemsOnPage(page: Int): List<Placed<T>> {
            val onPage = flowing.drop(page * pageSize).take(pageSize)
            return pinned + onPage.mapIndexed { index, item -> Placed(item, flowCells[index]) }
        }

        /** Cells a pinned item holds, to keep fillers and flowing items off them. */
        val pinnedCells: Set<Cell> get() = pinned.mapTo(HashSet()) { it.cell }
    }

    /**
     * Row-major cell of [slot] in a content area [columns] wide and [rows] high, or null when the
     * slot is negative (no explicit placement) or past the area.
     */
    fun cellOf(slot: Int, columns: Int, rows: Int): Cell? {
        if (slot < 0 || columns <= 0 || slot >= columns * rows) return null
        return Cell(slot % columns, slot / columns)
    }

    /**
     * Splits [items] between pinned and flowing.
     *
     * A slot outside the area, or already held by an earlier item, sends its item back to the flow
     * rather than hiding it.
     */
    fun <T> plan(
        items: List<T>,
        slotOf: (T) -> Int,
        markers: List<Cell>,
        columns: Int,
        rows: Int,
    ): PagePlan<T> {
        val pinned = mutableListOf<Placed<T>>()
        val flowing = mutableListOf<T>()
        val taken = mutableSetOf<Cell>()

        for (item in items) {
            val cell = cellOf(slotOf(item), columns, rows)
            if (cell != null && taken.add(cell)) pinned += Placed(item, cell) else flowing += item
        }

        val markerCells = markers.filter { it !in taken }
        if (flowing.isEmpty() || markerCells.isNotEmpty()) return PagePlan(pinned, flowing, markerCells)

        // Items still flow but no marker is left. When pinned items hold every marker, the flowing
        // ones go on the free cells of the content area, page after page, instead of vanishing.
        // A layout with no marker at all, or an area with no free cell, cannot show them: they are
        // reported as [PagePlan.unplaced].
        val freeCells = if (markers.isEmpty()) emptyList() else
            (0 until rows).flatMap { y -> (0 until columns).map { x -> Cell(x, y) } }.filter { it !in taken }
        return if (freeCells.isNotEmpty()) PagePlan(pinned, flowing, freeCells, usedFreeCells = true)
        else PagePlan(pinned, emptyList(), emptyList(), unplaced = flowing)
    }
}
