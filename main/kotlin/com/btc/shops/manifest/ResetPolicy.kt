package com.btc.shops.manifest

import com.typewritermc.core.extension.annotations.Help
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/** Structured policies describing when shop stocks are reset. */
@Serializable
sealed class ResetPolicy {
    @Serializable
    @SerialName("none")
    data object None : ResetPolicy()

    @Serializable
    @SerialName("cron")
    data class Cron(
        @Help("Cron expression (e.g., '0 0 * * *').")
        val expression: String = "0 0 * * *"
    ) : ResetPolicy()

    @Serializable
    @SerialName("interval")
    data class Interval(
        @Help("Interval in seconds.")
        val seconds: Long = 3600
    ) : ResetPolicy()

    @Serializable
    @SerialName("daily")
    data class Daily(
        @Help("Hour of day (0-23).")
        val hour: Int = 0,
        @Help("Minute of hour (0-59).")
        val minute: Int = 0
    ) : ResetPolicy()

    @Serializable
    @SerialName("weekly")
    data class Weekly(
        @Help("Day of week.")
        val day: DayOfWeek = DayOfWeek.MONDAY,
        @Help("Hour of day (0-23).")
        val hour: Int = 0,
        @Help("Minute of hour (0-59).")
        val minute: Int = 0
    ) : ResetPolicy()

    @Serializable
    @SerialName("monthly")
    data class Monthly(
        @Help("Day of month (1-31).")
        val day: Int = 1,
        @Help("Hour of day (0-23).")
        val hour: Int = 0,
        @Help("Minute of hour (0-59).")
        val minute: Int = 0
    ) : ResetPolicy()
}
