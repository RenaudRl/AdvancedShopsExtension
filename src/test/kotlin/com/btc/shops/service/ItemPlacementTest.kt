package com.btc.shops.service

import com.btc.shops.service.ItemPlacement.Cell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards where shop items land: an explicit slot pins an item, everything else flows through the
 * SHOP_ITEM markers, and no two items ever share a cell.
 */
class ItemPlacementTest {

    /** An item reduced to what placement looks at: its name and the slot it asks for. */
    private data class Article(val name: String, val guiSlot: Int = -1)

    private val twoByTwoMarkers = listOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1))

    private fun plan(items: List<Article>, markers: List<Cell> = twoByTwoMarkers, columns: Int = 9, rows: Int = 3) =
        ItemPlacement.plan(items, Article::guiSlot, markers, columns, rows)

    private fun ItemPlacement.PagePlan<Article>.names(page: Int) =
        itemsOnPage(page).associate { it.item.name to it.cell }

    @Test
    fun `a slot is counted row by row from the top left of the content area`() {
        assertEquals(Cell(0, 0), ItemPlacement.cellOf(0, columns = 9, rows = 3))
        assertEquals(Cell(8, 0), ItemPlacement.cellOf(8, columns = 9, rows = 3))
        assertEquals(Cell(0, 1), ItemPlacement.cellOf(9, columns = 9, rows = 3))
        assertEquals(Cell(8, 2), ItemPlacement.cellOf(26, columns = 9, rows = 3))
    }

    @Test
    fun `no explicit slot, or one outside the area, designates no cell`() {
        assertNull(ItemPlacement.cellOf(-1, columns = 9, rows = 3))
        assertNull(ItemPlacement.cellOf(27, columns = 9, rows = 3))
        assertNull(ItemPlacement.cellOf(0, columns = 0, rows = 3))
    }

    @Test
    fun `items without a slot fill the markers in declaration order`() {
        val plan = plan(listOf(Article("a"), Article("b"), Article("c")))

        assertEquals(
            mapOf("a" to Cell(0, 0), "b" to Cell(1, 0), "c" to Cell(0, 1)),
            plan.names(0),
        )
    }

    @Test
    fun `an item with a slot lands there, not on a marker`() {
        val plan = plan(listOf(Article("a"), Article("pinned", guiSlot = 13)))

        assertEquals(mapOf("pinned" to Cell(4, 1), "a" to Cell(0, 0)), plan.names(0))
    }

    @Test
    fun `a pinned item takes its marker away from the flow`() {
        // Slot 1 is the marker at (1, 0): the flowing items must go around it.
        val plan = plan(listOf(Article("pinned", guiSlot = 1), Article("a"), Article("b"), Article("c")))

        assertEquals(
            mapOf("pinned" to Cell(1, 0), "a" to Cell(0, 0), "b" to Cell(0, 1), "c" to Cell(1, 1)),
            plan.names(0),
        )
        assertEquals(3, plan.pageSize)
    }

    @Test
    fun `a pinned item stays in place on every page`() {
        val articles = listOf(Article("pinned", guiSlot = 20)) + (1..6).map { Article("f$it") }
        val plan = plan(articles)

        assertEquals(1, plan.lastPage)
        assertEquals(Cell(2, 2), plan.names(0)["pinned"])
        assertEquals(Cell(2, 2), plan.names(1)["pinned"])
        assertEquals(listOf("pinned", "f5", "f6"), plan.itemsOnPage(1).map { it.item.name })
    }

    @Test
    fun `two items asking for the same slot keep the first and send the second to the flow`() {
        val plan = plan(listOf(Article("first", guiSlot = 5), Article("second", guiSlot = 5)))

        assertEquals(Cell(5, 0), plan.names(0)["first"])
        assertEquals(Cell(0, 0), plan.names(0)["second"])
    }

    @Test
    fun `a slot outside the area sends the item back to the markers instead of hiding it`() {
        val plan = plan(listOf(Article("lost", guiSlot = 99)))

        assertEquals(mapOf("lost" to Cell(0, 0)), plan.names(0))
    }

    @Test
    fun `a shop whose every item is pinned needs no marker`() {
        val plan = plan(listOf(Article("a", guiSlot = 0), Article("b", guiSlot = 4)), markers = emptyList())

        assertEquals(mapOf("a" to Cell(0, 0), "b" to Cell(4, 0)), plan.names(0))
        assertEquals(0, plan.lastPage)
    }

    @Test
    fun `articles pushed off every marker by pinned ones go on the free cells`() {
        // 3x1 area, all three markers held by pinned articles: the 4th article has no marker left.
        val markers = listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val plan = plan(
            listOf(Article("p0", guiSlot = 0), Article("p1", guiSlot = 1), Article("p2", guiSlot = 2), Article("x")),
            markers = markers, columns = 3, rows = 2,
        )

        assertEquals(emptyList(), plan.unplaced)
        assertEquals(true, plan.usedFreeCells)
        assertEquals(Cell(0, 1), plan.names(0)["x"])
    }

    @Test
    fun `articles pushed off the markers spread over several pages of free cells`() {
        val markers = listOf(Cell(0, 0))
        val articles = listOf(Article("pinned", guiSlot = 0)) + (1..5).map { Article("f$it") }
        val plan = plan(articles, markers = markers, columns = 2, rows = 2)

        // Free cells: (1,0), (0,1), (1,1) = 3 per page, 5 flowing articles = 2 pages.
        assertEquals(3, plan.pageSize)
        assertEquals(1, plan.lastPage)
        assertEquals(listOf("pinned", "f1", "f2", "f3"), plan.itemsOnPage(0).map { it.item.name })
        assertEquals(listOf("pinned", "f4", "f5"), plan.itemsOnPage(1).map { it.item.name })
        assertEquals(emptyList(), plan.unplaced)
    }

    @Test
    fun `an article that fits no cell at all is reported as unplaced, not dropped silently`() {
        val full = (0 until 4).map { Article("p$it", guiSlot = it) }
        val plan = plan(full + Article("lost"), markers = listOf(Cell(0, 0)), columns = 2, rows = 2)

        assertEquals(listOf("lost"), plan.unplaced.map { it.name })
        assertEquals(false, plan.usedFreeCells)
    }

    @Test
    fun `a layout without any marker reports its flowing articles instead of using free cells`() {
        val plan = plan(listOf(Article("a")), markers = emptyList())

        assertEquals(listOf("a"), plan.unplaced.map { it.name })
    }

    @Test
    fun `a normal shop uses neither fallback`() {
        val plan = plan(listOf(Article("a"), Article("b")))

        assertEquals(false, plan.usedFreeCells)
        assertEquals(emptyList(), plan.unplaced)
    }

    @Test
    fun `identical articles keep their own position as stock index`() {
        val twin = Article("twin")
        val plan = ItemPlacement.plan(
            listOf(twin, Article("other"), twin).withIndex().toList(),
            { it.value.guiSlot }, twoByTwoMarkers, columns = 9, rows = 3,
        )

        // The 1st and 3rd articles are equal: indexOf would give both index 0.
        assertEquals(listOf(0, 1, 2), plan.itemsOnPage(0).map { it.item.index })
    }

    @Test
    fun `a tab shop counts its slots in the width of its content area`() {
        val plan = plan(listOf(Article("pinned", guiSlot = 5)), markers = emptyList(), columns = 3, rows = 3)

        assertEquals(Cell(2, 1), plan.names(0)["pinned"])
    }
}
