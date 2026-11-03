package io.github.jameshome.galley.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 元信息。
 *
 * <p>契约由实现生成（springdoc），这里只补标题与版本；版本从 Maven 资源过滤注入， 与 {@code pom.xml} 同源，避免契约版本手改后与制品不一致。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI galleyOpenApi(@Value("${galley.version:dev}") String version) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("jameshome-galley API")
                                .description("团体厨房库存远程查看系统的接口契约，由实现生成。")
                                .version(version));
    }
}
