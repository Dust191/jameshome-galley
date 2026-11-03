package io.github.jameshome.galley.modules.stock.api;

import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.ReconcileReport;
import io.github.jameshome.galley.modules.stock.application.ReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 结存对账（运维用，仅管理员）。 */
@RestController
@RequestMapping("/api/v1/reconcile")
public class ReconcileController {

    private final ReconciliationService service;

    public ReconcileController(ReconciliationService service) {
        this.service = service;
    }

    @GetMapping
    public ReconcileReport report() {
        return service.report();
    }
}
