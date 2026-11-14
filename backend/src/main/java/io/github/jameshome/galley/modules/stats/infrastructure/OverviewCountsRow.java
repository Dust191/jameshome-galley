package io.github.jameshome.galley.modules.stats.infrastructure;

/** 总览页五项计数的一次查询结果（M-1 / M-2 / M-3 / M-7 / M-8）。 */
public class OverviewCountsRow {

    private Long materials;
    private Long low;
    private Long zero;
    private Long stale;
    private Long complete;

    public Long getMaterials() {
        return materials;
    }

    public void setMaterials(Long materials) {
        this.materials = materials;
    }

    public Long getLow() {
        return low;
    }

    public void setLow(Long low) {
        this.low = low;
    }

    public Long getZero() {
        return zero;
    }

    public void setZero(Long zero) {
        this.zero = zero;
    }

    public Long getStale() {
        return stale;
    }

    public void setStale(Long stale) {
        this.stale = stale;
    }

    public Long getComplete() {
        return complete;
    }

    public void setComplete(Long complete) {
        this.complete = complete;
    }
}
