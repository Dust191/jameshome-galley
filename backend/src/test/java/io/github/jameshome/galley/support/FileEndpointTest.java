package io.github.jameshome.galley.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.github.jameshome.galley.common.storage.ObjectStorage;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 上传端点。对象存储用 mock 替换：这里验证的是"字节识别 + 状态码 + 返回体"， 不验证 MinIO 本身（那属于部署验证）。 */
class FileEndpointTest extends IntegrationTestBase {

    private static final byte[] PNG =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAFUlEQVR42mP8z8BQz0AEYBxVSF+FAP5FDvcfRYWgAAAAAElFTkSuQmCC");

    @MockitoBean ObjectStorage storage;

    @Test
    void upload_png返回key与url() {
        when(storage.publicUrl(anyString()))
                .thenAnswer(invocation -> "http://storage.test/" + invocation.getArgument(0));

        ApiClient.Response response = asAdmin().upload("/api/v1/files", "a.png", PNG, "image/png");

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.text("/key")).startsWith("material/").endsWith(".png");
        assertThat(response.text("/url")).startsWith("http://storage.test/material/");
    }

    @Test
    void upload_伪装成图片的文本被拒() {
        ApiClient.Response response =
                asAdmin()
                        .upload(
                                "/api/v1/files",
                                "evil.png",
                                "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                "image/png");

        assertThat(response.status()).isEqualTo(415);
        assertThat(response.text("/code")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void upload_空文件返回400() {
        ApiClient.Response response =
                asAdmin().upload("/api/v1/files", "empty.png", new byte[0], "image/png");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("/code")).isEqualTo("VALIDATION_FAILED");
    }
}
