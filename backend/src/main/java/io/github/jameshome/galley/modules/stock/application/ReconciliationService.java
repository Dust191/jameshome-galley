package io.github.jameshome.galley.modules.stock.application;

import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.Mismatch;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.ReconcileReport;
import io.github.jameshome.galley.modules.stock.infrastructure.ReconcileMapper;
import io.github.jameshome.galley.modules.stock.infrastructure.ReconcileRow;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 结存对账（DES-001 I-5）：校验"结存 = 流水求和"。
 *
 * <p>只报告，不自动修：差异说明有 bug 或有人绕过应用改了库，自动"修平"会把证据一起抹掉。 每日定时跑一次，发现差异告警；运维也可以在发布后手动触发同一个逻辑。
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconcileMapper mapper;

    public ReconciliationService(ReconcileMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ReconcileReport report() {
        List<ReconcileRow> rows = mapper.findMismatches();
        return new ReconcileReport(
                mapper.countMaterials(),
                rows.size(),
                Instant.now(),
                rows.stream()
                        .map(
                                row ->
                                        new Mismatch(
                                                row.getMaterialId().toString(),
                                                row.getName(),
                                                row.getBalance().toPlainString(),
                                                row.getMovementSum().toPlainString()))
                        .toList());
    }

    /** 每日 02:30（可配置）。差异只告警，不改数据。 */
    @Scheduled(cron = "${galley.reconcile.cron:0 30 2 * * *}")
    public void daily() {
        ReconcileReport report = report();
        if (report.mismatched() == 0) {
            log.info("结存对账通过：{} 个物料零差异", report.checked());
        } else {
            log.warn(
                    "结存对账发现 {} 个差异（共核对 {} 个物料），需人工排查，不自动修复", report.mismatched(), report.checked());
        }
    }
}
