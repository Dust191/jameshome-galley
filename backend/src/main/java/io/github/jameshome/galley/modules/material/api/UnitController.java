package io.github.jameshome.galley.modules.material.api;

import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.UnitCreate;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.UnitPatch;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.UnitView;
import io.github.jameshome.galley.modules.material.application.UnitService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/units")
public class UnitController {

    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping
    public List<UnitView> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive).stream().map(UnitView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitView create(@Valid @RequestBody UnitCreate body) {
        int sort = body.sortOrder() == null ? 0 : body.sortOrder();
        return UnitView.from(service.create(body.name(), sort));
    }

    @PatchMapping("/{id}")
    public UnitView update(@PathVariable UUID id, @Valid @RequestBody UnitPatch body) {
        return UnitView.from(service.update(id, body.name(), body.sortOrder(), body.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
