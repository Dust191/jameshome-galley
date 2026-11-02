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
     * <p>文件名用 ASCII 兜底 + {@code filename*} 给中文名：部分旧客户端只认前者， 而现代浏览器优先取后者。
     */
    @GetMapping("/materials.csv")
    public ResponseEntity<byte[]> exportMaterials(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String stockState,
            @RequestParam(required = false) String status) {

        byte[] body = service.exportMaterials(keyword, categoryId, stockState, status);
        String filename = "materials-" + LocalDate.now() + ".csv";

        ContentDisposition disposition =
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
