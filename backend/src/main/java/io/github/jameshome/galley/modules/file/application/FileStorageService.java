package io.github.jameshome.galley.modules.file.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.common.storage.ObjectStorage;
import io.github.jameshome.galley.common.storage.StorageProperties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 图片上传。
 *
 * <p>只接受三种位图格式，按 **实际字节头**判断：客户端声明的 Content-Type 可伪造，只用于交叉验证。 文件名不参与拼
 * key——用户给的名字可能带路径分隔符，直接拼接等于开放目录穿越。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** 允许的类型与扩展名。不收录 svg：svg 可内嵌脚本，作为用户上传内容风险更高。 */
    private static final Map<String, String> ALLOWED =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private static final DateTimeFormatter PATH_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final ObjectStorage storage;
    private final StorageProperties properties;

    public FileStorageService(ObjectStorage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * 保存图片并返回对象 key。
     *
     * @param declaredContentType 客户端声明的类型，仅用于交叉验证
     */
    public String storeImage(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件为空", "file");
        }
        if (content.length > properties.maxImageBytes()) {
            throw new BusinessException(
                    ErrorCode.UPLOAD_TOO_LARGE,
                    "图片超过 " + (properties.maxImageBytes() / 1024 / 1024) + " MB",
                    "file");
        }

        String actualType = detectType(content);
        if (actualType == null) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE, "只支持 JPEG、PNG、WebP 三种图片格式", "file");
        }
        if (declaredContentType != null
                && !declaredContentType.isBlank()
                && !declaredContentType.equalsIgnoreCase(actualType)) {
            log.debug("声明类型 {} 与实际 {} 不一致，按实际处理", declaredContentType, actualType);
        }

        String key =
                "material/"
                        + LocalDate.now().format(PATH_MONTH)
                        + "/"
                        + UuidV7.generate()
                        + "."
                        + ALLOWED.get(actualType);
        storage.put(key, content, actualType);
        return key;
    }

    public String urlOf(String key) {
        return key == null || key.isBlank() ? null : storage.publicUrl(key);
    }

    /** 按字节头判断真实类型；识别不出返回 null。 */
    static String detectType(byte[] b) {
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8
                && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P'
                && b[2] == 'N'
                && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D
                && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A
                && (b[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
