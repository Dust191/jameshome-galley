package io.github.jameshome.galley.modules.stats.infrastructure;

import java.time.LocalDate;

/** M-5 趋势的一个点；缺日由 SQL 补齐为 0。 */
public class TrendRow {

    private LocalDate day;
    private Long inCount;
    private Long outCount;

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public Long getInCount() {
        return inCount;
    }

    public void setInCount(Long inCount) {
        this.inCount = inCount;
    }

    public Long getOutCount() {
        return outCount;
    }

    public void setOutCount(Long outCount) {
        this.outCount = outCount;
    }
}
