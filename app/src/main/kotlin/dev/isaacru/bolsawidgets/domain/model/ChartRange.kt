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

    /**
     * How stale a cached series of this span may get before refetching is worth the
     * network. A one-day chart moves every few minutes; a one-year chart does not change
     * meaningfully within a day.
     */
    val cacheMaxAge: java.time.Duration
        get() = when (this) {
            DAY -> java.time.Duration.ofMinutes(15)
            WEEK -> java.time.Duration.ofHours(1)
            MONTH -> java.time.Duration.ofHours(6)
            YEAR -> java.time.Duration.ofHours(24)
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
