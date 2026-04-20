package com.btc.shops.manifest

import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.AlgebraicTypeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Structured policies describing when shop stocks are reset. */
@Serializable
@AlgebraicTypeInfo("reset_policy", "white", "mdi:clock-outline")
sealed interface ResetPolicy {
    @Serializable
    @SerialName("none")
    @AlgebraicTypeInfo("none", "gray", "mdi:close")
    data class None(val _dummy: Boolean = false) : ResetPolicy

    @Serializable
    @SerialName("cron")
    @AlgebraicTypeInfo("cron", "blue", "mdi:calendar-clock")
    data class Cron(
        @Help("Cron expression (e.g., '0 0 * * *').")
        val expression: String = "0 0 * * *"
    ) : ResetPolicy

    @Serializable
    @SerialName("interval")
    @AlgebraicTypeInfo("interval", "green", "mdi:timer-outline")
    data class Interval(
        @Help("Interval in seconds.")
        val seconds: Long = 3600
    ) : ResetPolicy

    @Serializable
    @SerialName("daily")
    @AlgebraicTypeInfo("daily", "yellow", "mdi:weather-sunny")
    data class Daily(
        @Help("Hour of day (0-23).")
        val hour: Int = 0,
        @Help("Minute of hour (0-59).")
        val minute: Int = 0
    ) : ResetPolicy

    @Serializable
    @SerialName("weekly")
    @AlgebraicTypeInfo("weekly", "orange", "mdi:calendar-week")
    data class Weekly(
        @Help("Day of week (1-7, where 1 is Monday).")
        val day: Int = 1,
        @Help("Hour of day (0-23).")
        val hour: Int = 0,
        @Help("Minute of hour (0-59).")
        val minute: Int = 0
    ) : ResetPolicy

    @Serializable
    @SerialName("monthly")
    @AlgebraicTypeInfo("monthly", "red", "mdi:calendar-month")
    data class Monthly(
        @Help("Day of month (1-31).")
        val day: Int = 1,
        @Help("Hour of day (0-23).")
        val hour: Int = 0,
        @Help("Minute of hour (0-59).")
        val minute: Int = 0
    ) : ResetPolicy
}
