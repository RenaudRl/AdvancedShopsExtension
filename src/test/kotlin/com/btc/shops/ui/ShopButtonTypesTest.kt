package com.btc.shops.ui

import btcrenaud.gui.editor.api.ButtonTypeRegistry
import btcrenaud.gui.editor.api.MenuEditContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShopButtonTypesTest {

    private fun menu(entryType: String) = object : MenuEditContext {
        override val entryId = "test"
        override val entryType = entryType
        override fun stringField(name: String): String? = null
    }

    @BeforeTest
    fun register() = ShopButtonTypes.register()

    @AfterTest
    fun unregister() = ShopButtonTypes.unregister()

    @Test
    fun `every type the resolver parses is declared under the resolver prefix`() {
        val context = menu("shop_definition")

        for (type in ShopButtonType.entries) {
            assertEquals(
                true,
                ButtonTypeRegistry.isKnownButtonType(type.name, ShopButtonResolverLayout.PREFIX, context),
                "${type.name} is parsed by ShopButtonResolverLayout but not declared to the editor",
            )
        }
    }

    @Test
    fun `parameterised amounts stay known through their base type`() {
        assertEquals(true, ButtonTypeRegistry.isKnownButtonType("BUY_1:10", ShopButtonResolverLayout.PREFIX, menu("shop_definition")))
    }

    @Test
    fun `no declared type is unknown to the resolver`() {
        val declared = ShopButtonTypes.contribution.types.map { it.id }

        assertEquals(ShopButtonType.entries.map { it.name }.sorted(), declared.sorted())
    }

    @Test
    fun `a type the resolver does not parse is reported unknown`() {
        assertEquals(false, ButtonTypeRegistry.isKnownButtonType("NOT_A_BUTTON", ShopButtonResolverLayout.PREFIX, menu("shop_definition")))
    }

    @Test
    fun `other entry types are not offered shop buttons`() {
        assertNull(ButtonTypeRegistry.isKnownButtonType("CLOSE", ShopButtonResolverLayout.PREFIX, menu("open_gui")))
    }

    @Test
    fun `unregistering removes the contribution`() {
        ShopButtonTypes.unregister()

        assertFalse(ButtonTypeRegistry.all().any { it.extensionId == ShopButtonTypes.EXTENSION_ID })
        assertTrue(ButtonTypeRegistry.all().none { it.prefix == ShopButtonResolverLayout.PREFIX })
    }
}
