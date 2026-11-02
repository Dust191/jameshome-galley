package io.github.jameshome.galley.common.mybatis;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import java.util.UUID;
import org.apache.ibatis.type.JdbcType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册全局类型处理器与乐观锁插件。 */
@Configuration
public class MyBatisConfig {

    @Bean
    ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> {
            UuidTypeHandler handler = new UuidTypeHandler();
            var registry = configuration.getTypeHandlerRegistry();
            registry.register(UUID.class, handler);
            registry.register(UUID.class, JdbcType.OTHER, handler);
            registry.register(UUID.class, JdbcType.VARCHAR, handler);
        };
    }

    /**
     * 乐观锁：{@code MaterialDO.version} 带 {@code @Version}，更新时自动追加 {@code WHERE version = ?} 并自增；影响行数为
     * 0 说明被别人改过，由 Service 转 409。
     */
    @Bean
    MybatisPlusInterceptor optimisticLockInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
