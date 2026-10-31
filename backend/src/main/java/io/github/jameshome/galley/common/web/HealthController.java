package io.github.jameshome.galley.common.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 骨架期的存活探针。不查库，不依赖任何业务模块，用于容器编排与网关的快速探活。
 *
 * <p>就绪探针走 /actuator/health/readiness（查库），两者职责不同，不可互相替代。
 */
@RestController
public class HealthController {

    private final String version;

    public HealthController(@Value("${galley.version}") String version) {
        this.version = version;
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("version", version);
        return body;
    }
}
