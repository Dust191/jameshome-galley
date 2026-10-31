package io.github.jameshome.galley.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 存活探针的契约：不依赖容器、不查库，返回固定的两个字段。 */
class HealthControllerTest {

    @Test
    void shouldReturnOkWithVersion() {
        HealthController controller = new HealthController("0.0.1");

        Map<String, String> body = controller.healthz();

        assertThat(body).containsEntry("status", "ok").containsEntry("version", "0.0.1");
    }
}
