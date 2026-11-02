package io.github.jameshome.galley.modules.stock.api;

import io.github.jameshome.galley.common.web.PageResult;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.BalanceView;
import io.github.jameshome.galley.modules.stock.application.BalanceService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/balances")
public class BalanceController {

    private final BalanceService service;

    public BalanceController(BalanceService service) {
        this.service = service;
    }

    /** {@code stockState} 取 {@code negative} / {@code zero} / {@code low} / {@code stale}。 */
    @GetMapping
    public PageResult<BalanceView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String stockState,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return service.search(keyword, categoryId, stockState, sort, page, pageSize);
    }
}
