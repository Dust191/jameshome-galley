package io.github.jameshome.galley.modules.export.api;

import io.github.jameshome.galley.modules.export.application.MaterialExportService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final MaterialExportService service;

    public ExportController(MaterialExportService service) {
        this.service = service;
    }

    /**
     * 物料 CSV 导出。
     *
     * <p>受当前筛选约束，不受分页限制。
     */
    @GetMapping("/materials.csv")
    public ResponseEntity<byte[]> exportMaterials(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String stockState,
            @RequestParam(required = false) String status) {

        byte[] body = service.exportMaterials(keyword, categoryId, stockState, status);
        return csv(body, "materials");
    }

    /** 流水 CSV 导出；筛选参数与列表一致。 */
    @GetMapping("/movements.csv")
    public ResponseEntity<byte[]> exportMovements(
            @RequestParam(required = false) String materialId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        byte[] body = service.exportMovements(materialId, type, from, to);
        return csv(body, "movements");
    }

    /**
     * 两个导出共用响应头。
     *
     * <p>文件名用 {@code ContentDisposition} 的 UTF-8 形式：直接往 header 里塞中文会被部分容器截断。
     */
    private static ResponseEntity<byte[]> csv(byte[] body, String prefix) {
        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(prefix + "-" + LocalDate.now() + ".csv", StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
