package dev.isaacru.bolsawidgets.domain.model

/** Time span of a chart request. Labels are the ones shown in the UI (Spanish). */
enum class ChartRange(val label: String) {
    DAY("1D"),
    WEEK("1S"),
    MONTH("1M"),
    YEAR("1A");

    /** Bar size that gives a readable chart for this span without over-fetching. */
    val defaultInterval: CandleInterval
        get() = when (this) {
            DAY -> CandleInterval.MINUTE_5
            WEEK -> CandleInterval.MINUTE_30
            MONTH -> CandleInterval.DAY_1
            YEAR -> CandleInterval.DAY_1
        }
}

/** Bar size of a chart request. */
enum class CandleInterval {
    MINUTE_1,
    MINUTE_5,
    MINUTE_15,
    MINUTE_30,
    HOUR_1,
    DAY_1,
    WEEK_1,
}
