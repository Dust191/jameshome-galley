package io.github.jameshome.galley.common.id;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * UUID v7：前 48 位为毫秒时间戳，其余为随机位。
 *
 * <p>选它而不是 UUID v4，是因为主键带时间前缀后索引局部性好；也不用自增 ID，避免暴露数据规模（GOV-000 §5.5 数据规范）。
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    public static java.util.UUID generate() {
        byte[] value = new byte[16];
        long ts = Instant.now().toEpochMilli();

        value[0] = (byte) (ts >>> 40);
        value[1] = (byte) (ts >>> 32);
        value[2] = (byte) (ts >>> 24);
        value[3] = (byte) (ts >>> 16);
        value[4] = (byte) (ts >>> 8);
        value[5] = (byte) ts;

        byte[] tail = new byte[10];
        RANDOM.nextBytes(tail);
        System.arraycopy(tail, 0, value, 6, 10);

        // version = 7
        value[6] = (byte) ((value[6] & 0x0F) | 0x70);
        // variant = RFC 4122
        value[8] = (byte) ((value[8] & 0x3F) | 0x80);

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (value[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (value[i] & 0xFF);
        }
        return new java.util.UUID(msb, lsb);
    }
}
