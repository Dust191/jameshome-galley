package io.github.jameshome.galley.modules.stats.infrastructure;

/** M-4 分类分布的一行。 */
public class CategoryRow {

    private String categoryName;
    private Boolean inactive;
    private Long materialCount;

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Boolean getInactive() {
        return inactive;
    }

    public void setInactive(Boolean inactive) {
        this.inactive = inactive;
    }

    public Long getMaterialCount() {
        return materialCount;
    }

    public void setMaterialCount(Long materialCount) {
        this.materialCount = materialCount;
    }
}
