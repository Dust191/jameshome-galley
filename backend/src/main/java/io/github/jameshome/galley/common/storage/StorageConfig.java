package io.github.jameshome.galley.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储装配。
 *
 * <p>用 {@link ConditionalOnMissingBean} 而不是直接 new：S5 接 OSS 时新增一个 {@code OssObjectStorage} 的
 * {@code @Bean} 即可覆盖，不改这里。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    ObjectStorage objectStorage(StorageProperties properties) {
        return new MinioObjectStorage(properties);
    }
}
