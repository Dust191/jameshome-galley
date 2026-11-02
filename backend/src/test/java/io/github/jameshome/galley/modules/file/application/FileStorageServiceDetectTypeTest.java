package io.github.jameshome.galley.modules.file.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 图片类型识别。
 *
 * <p>只信任字节头，不信任客户端声明的 Content-Type：后者由客户端随意填写， 若据此放行，改个后缀就能把任意文件塞进对象存储。
 */
class FileStorageServiceDetectTypeTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A, 0, 0
    };

    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

    @Test
    @DisplayName("识别三种允许的位图格式")
    void detectsAllowedTypes() {
        assertThat(FileStorageService.detectType(JPEG)).isEqualTo("image/jpeg");
        assertThat(FileStorageService.detectType(PNG)).isEqualTo("image/png");
        assertThat(FileStorageService.detectType(WEBP)).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("SVG 不被接受：可内嵌脚本，作为用户上传内容风险过高")
    void rejectsSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
        assertThat(FileStorageService.detectType(svg)).isNull();
    }

    @Test
    @DisplayName("伪装成图片的可执行文件被拒绝")
    void rejectsDisguisedBinary() {
        byte[] elf = {0x7F, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00};
        assertThat(FileStorageService.detectType(elf)).isNull();
    }

    @Test
    @DisplayName("空内容与不足头的短内容不会越界")
    void handlesShortInput() {
        assertThat(FileStorageService.detectType(new byte[0])).isNull();
        assertThat(FileStorageService.detectType(new byte[] {(byte) 0x89})).isNull();
        assertThat(FileStorageService.detectType(new byte[] {(byte) 0xFF, (byte) 0xD8})).isNull();
    }

    @Test
    @DisplayName("RIFF 但不是 WEBP（如 WAV）不被误判")
    void riffButNotWebp() {
        byte[] wav = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
        assertThat(FileStorageService.detectType(wav)).isNull();
    }
}
