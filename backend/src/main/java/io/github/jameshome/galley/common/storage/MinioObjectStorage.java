package io.github.jameshome.galley.common.storage;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import okhttp3.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 S3 协议的实现，开发环境指向 MinIO（ADR-0005）。
 *
 * <p>桶采用"首次写入时按需创建"：开发机上桶可能还没建，直接抛错会让人以为是配置问题； 但只在 dev 语义下合理，生产桶应由运维预先创建并配好权限，故此处只做兜底。
 */
public class MinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);

    private final MinioClient client;
    private final String bucket;
    private final String publicBase;

    private volatile boolean bucketReady;

    public MinioObjectStorage(StorageProperties properties) {
        this.client =
                MinioClient.builder()
                        .endpoint(properties.endpoint())
                        .credentials(properties.accessKey(), properties.secretKey())
                        .build();
        this.bucket = properties.bucket();
        this.publicBase = stripTrailingSlash(properties.publicBase());
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        ensureBucket();
        try (InputStream in = new ByteArrayInputStream(content)) {
            client.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(key).stream(
                                    in, content.length, -1)
                            .contentType(
                                    contentType == null ? "application/octet-stream" : contentType)
                            .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.STORAGE_FAILED, "对象存储写入失败：" + ex.getMessage());
        }
    }

    @Override
    public StoredObject get(String key) {
        try (GetObjectResponse response =
                client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return new StoredObject(response.readAllBytes(), contentTypeOf(response.headers()));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对象不存在：" + key);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            // 删除失败不影响主流程：图片换新后旧对象残留只是浪费空间，不该让业务失败
            log.warn("删除对象失败 key={}：{}", key, ex.getMessage());
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBase + "/" + key;
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists =
                        client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("已创建对象存储桶 {}", bucket);
                }
                bucketReady = true;
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.STORAGE_FAILED, "对象存储不可用：" + ex.getMessage());
            }
        }
    }

    private static String contentTypeOf(Headers headers) {
        String type = headers == null ? null : headers.get("Content-Type");
        return type == null ? "application/octet-stream" : type;
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
