package io.github.jameshome.galley.modules.stock.api;

import io.github.jameshome.galley.common.web.PageResult;
import io.github.jameshome.galley.modules.identity.application.CurrentUserService;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.MovementCreate;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.MovementView;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.ReversalRequest;
import io.github.jameshome.galley.modules.stock.application.MovementService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movements")
public class MovementController {

    private final MovementService service;
    private final CurrentUserService currentUser;

    public MovementController(MovementService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResult<MovementView> list(
            @RequestParam(required = false) UUID materialId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return service.search(materialId, type, from, to, page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovementView create(@Valid @RequestBody MovementCreate body) {
        return service.create(body, currentUser.require());
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementView reverse(@PathVariable UUID id, @Valid @RequestBody ReversalRequest body) {
        return service.reverse(id, body.note(), currentUser.require());
    }
}
