package io.github.jameshome.galley.modules.stats.api;

import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.AlertList;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.CategoryShare;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.OverviewView;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.TopOutItem;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.TrendPoint;
import io.github.jameshome.galley.modules.stats.application.StatsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public OverviewView overview() {
        return service.overview();
    }

    @GetMapping("/category-distribution")
    public List<CategoryShare> categoryDistribution() {
        return service.categoryDistribution();
    }

    /** 固定 30 个点，缺日补 0（DES-004 M-5）。 */
    @GetMapping("/trend")
    public List<TrendPoint> trend() {
        return service.trend();
    }

    @GetMapping("/top-out")
    public List<TopOutItem> topOut(@RequestParam(defaultValue = "10") int limit) {
        return service.topOut(limit);
    }

    @GetMapping("/alerts")
    public AlertList alerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return service.alerts(page, pageSize);
    }
}
