package com.btc.shops.service

import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.btc.shops.manifest.TaxMode
import com.typewritermc.core.extension.annotations.Singleton

/**
 * Applies taxes to shop transactions.
 * Supports both global tax rate and per-item overrides.
 */
@Singleton
class TaxService {

    /**
     * Apply buy tax: increases the cost the player pays.
     * Returns the taxed amount (cost + tax).
     */
    fun applyBuyTax(baseCost: Double, definition: ShopDefinitionEntry, cfg: ShopItemConfig? = null): Double {
        val rate = getEffectiveTaxRate(definition, cfg)
        if (rate <= 0.0) return baseCost
        return baseCost * (1.0 + rate / 100.0)
    }

    /**
     * Apply sell tax: reduces the reward the player receives.
     * Returns the taxed amount (reward - tax).
     */
    fun applySellTax(baseReward: Double, definition: ShopDefinitionEntry, cfg: ShopItemConfig? = null): Double {
        val rate = getEffectiveTaxRate(definition, cfg)
        if (rate <= 0.0) return baseReward
        return baseReward * (1.0 - rate / 100.0)
    }

    /**
     * Returns only the tax portion (for logging/display).
     */
    fun getTaxAmount(baseAmount: Double, definition: ShopDefinitionEntry, cfg: ShopItemConfig? = null): Double {
        val rate = getEffectiveTaxRate(definition, cfg)
        if (rate <= 0.0) return 0.0
        return baseAmount * rate / 100.0
    }

    private fun getEffectiveTaxRate(definition: ShopDefinitionEntry, cfg: ShopItemConfig?): Double {
        if (definition.taxMode == TaxMode.PER_ITEM && cfg != null && cfg.taxRateOverride >= 0.0) {
            return cfg.taxRateOverride
        }
        return definition.taxRate
    }
}
