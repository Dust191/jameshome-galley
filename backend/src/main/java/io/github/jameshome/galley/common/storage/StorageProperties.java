package io.github.jameshome.galley.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置，键为 {@code galley.storage.*}。
 *
 * <p>凭据来自环境变量，不写进仓库；{@code region} 必须显式给出——签名前若让 SDK 自己去查桶所在区域，会先发一次 {@code
 * GetBucketLocation}，在私有桶或内网端点上容易变成运行期故障。
 *
 * <p>{@code endpoint} 供后端读写，{@code public-endpoint} 只用于签发浏览器可访问的地址——生产上前者是内网地址，后者是公网地址，
 * 两者不能混用，否则签出来的图片地址浏览器打不开。
 *
 * <p>{@code signed-url-ttl-seconds} 是图片地址的有效期。桶为私有读，地址必须带签名；有效期越短越安全，但页面停留超过有效期后 图片会失效，默认取 1 小时。
 */
@ConfigurationProperties(prefix = "galley.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        int signedUrlTtlSeconds,
        long maxImageBytes) {

    /** S3 签名允许的最长有效期：7 天。 */
    private static final int MAX_TTL_SECONDS = 7 * 24 * 3600;

    public StorageProperties {
        // 后端读写走内网 endpoint，浏览器只能访问公网 endpoint；未单独给出时二者相同（开发环境 MinIO）
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            publicEndpoint = endpoint;
        }
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        if (signedUrlTtlSeconds <= 0) {
            signedUrlTtlSeconds = 3600;
        }
        if (signedUrlTtlSeconds > MAX_TTL_SECONDS) {
            signedUrlTtlSeconds = MAX_TTL_SECONDS;
        }
        if (maxImageBytes <= 0) {
            maxImageBytes = 5L * 1024 * 1024;
        }
    }
}
