package io.github.jameshome.galley.modules.material.api;

import io.github.jameshome.galley.common.web.PageResult;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.MaterialCreate;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.MaterialPatch;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.MaterialView;
import io.github.jameshome.galley.modules.material.application.MaterialService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<MaterialView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String stockState,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return service.search(keyword, categoryId, stockState, status, sort, page, pageSize);
    }

    @GetMapping("/{id}")
    public MaterialView detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialView create(@Valid @RequestBody MaterialCreate body) {
        return service.create(body);
    }

    @PatchMapping("/{id}")
    public MaterialView update(@PathVariable UUID id, @Valid @RequestBody MaterialPatch body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.softDelete(id);
    }
}
