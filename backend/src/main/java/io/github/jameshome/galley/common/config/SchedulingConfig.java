package io.github.jameshome.galley.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 *
 * <p>目前只有结存对账一项。用 {@code galley.reconcile.scheduled} 控制：测试与本地调试可以关掉， 避免后台线程干扰；生产默认开启。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "galley.reconcile.scheduled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfig {}
