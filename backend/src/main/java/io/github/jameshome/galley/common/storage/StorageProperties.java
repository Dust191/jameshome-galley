package io.github.jameshome.galley.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置，键为 {@code galley.storage.*}。
 *
 * <p>凭据来自环境变量，不写进仓库；{@code public-base} 是浏览器可访问的基地址， 与 {@code endpoint} 可能不同（开发环境后端走内网地址、前端走宿主地址）。
 */
@ConfigurationProperties(prefix = "galley.storage")
public record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String publicBase,
        long maxImageBytes) {

    public StorageProperties {
        if (maxImageBytes <= 0) {
            maxImageBytes = 5L * 1024 * 1024;
        }
    }
}
