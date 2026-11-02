package io.github.jameshome.galley.modules.file.api;

import io.github.jameshome.galley.modules.file.application.FileStorageService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传。当前只用于物料图片，故路径与权限按物料图片的语义给。
 *
 * <p>返回 {@code key} 与 {@code url} 两个值：建档时存 {@code key}（ADR-0005）， {@code url} 仅供前端立即预览，不入库。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileStorageService service;

    public FileController(FileStorageService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        String key = service.storeImage(file.getBytes(), file.getContentType());
        return new UploadResponse(key, service.urlOf(key));
    }

    public record UploadResponse(String key, String url) {}
}
