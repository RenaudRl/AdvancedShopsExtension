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

        return PagePlan(pinned, flowing, markers.filter { it !in taken })
    }
}
