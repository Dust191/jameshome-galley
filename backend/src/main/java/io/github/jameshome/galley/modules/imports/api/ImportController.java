package io.github.jameshome.galley.modules.imports.api;

import io.github.jameshome.galley.modules.imports.application.MaterialImportService;
import io.github.jameshome.galley.modules.imports.application.MaterialImportService.ImportResult;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 批量导入。
 *
 * <p>即使有错误行也返回 200：请求本身是成功的，失败的是数据。 用 4xx 会让前端把"有几行填错"当成"接口调不通"，反而不展示逐行错误。
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final MaterialImportService service;

    public ImportController(MaterialImportService service) {
        this.service = service;
    }

    @PostMapping("/materials")
    public ImportResult importMaterials(@RequestParam("file") MultipartFile file)
            throws IOException {
        return service.importMaterials(file.getBytes());
    }
}
