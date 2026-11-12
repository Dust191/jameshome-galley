package io.github.jameshome.galley.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 预签名地址的生成规则：不依赖真实对象存储，只验证签名参数与有效期。 */
class MinioObjectStorageTest {

    private static MinioObjectStorage storage(int ttlSeconds) {
        return new MinioObjectStorage(
                new StorageProperties(
                        "http://127.0.0.1:9000",
                        "http://127.0.0.1:9000",
                        "us-east-1",
                        "ak",
                        "sk",
                        "galley-material",
                        ttlSeconds,
                        1024));
    }

    @Test
    void signedUrl_带签名参数与有效期() {
        String url = storage(600).signedUrl("material/2026-11/a.png");

        assertThat(url)
                .contains("galley-material")
                .contains("material/2026-11/a.png")
                .contains("X-Amz-Signature=")
                .contains("X-Amz-Expires=600");
    }

    @Test
    void 有效期被限制在七天以内() {
        String url = storage(30 * 24 * 3600).signedUrl("material/a.png");

        assertThat(url).contains("X-Amz-Expires=604800");
    }

    @Test
    void 未配置有效期时取一小时() {
        String url = storage(0).signedUrl("material/a.png");

        assertThat(url).contains("X-Amz-Expires=3600");
    }
}
