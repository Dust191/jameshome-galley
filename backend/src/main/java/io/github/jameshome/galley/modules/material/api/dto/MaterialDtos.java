package io.github.jameshome.galley.modules.material.api.dto;

import io.github.jameshome.galley.modules.material.domain.Category;
import io.github.jameshome.galley.modules.material.domain.DictStatus;
import io.github.jameshome.galley.modules.material.domain.Unit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** material 模块的出入参。分类与单位先落，物料的 DTO 在 T3 后半段补。 */
public final class MaterialDtos {

    private MaterialDtos() {}

    public record CategoryView(UUID id, String name, UUID parentId, int sortOrder, String status) {
        public static CategoryView from(Category c) {
            return new CategoryView(
                    c.id(), c.name(), c.parentId(), c.sortOrder(), c.status().dbValue());
        }
    }

    public record CategoryCreate(
            @NotBlank(message = "分类名不能为空") @Size(max = 50, message = "分类名不超过 50 字") String name,
            @Min(value = -32768, message = "排序值过小") @Max(value = 32767, message = "排序值过大")
                    Integer sortOrder) {}

    public record CategoryPatch(String name, Integer sortOrder, DictStatus status) {}

    public record UnitView(UUID id, String name, int sortOrder, String status) {
        public static UnitView from(Unit u) {
            return new UnitView(u.id(), u.name(), u.sortOrder(), u.status().dbValue());
        }
    }

    public record UnitCreate(
            @NotBlank(message = "单位名不能为空") @Size(max = 20, message = "单位名不超过 20 字") String name,
            @Min(value = -32768, message = "排序值过小") @Max(value = 32767, message = "排序值过大")
                    Integer sortOrder) {}

    public record UnitPatch(String name, Integer sortOrder, DictStatus status) {}
}
