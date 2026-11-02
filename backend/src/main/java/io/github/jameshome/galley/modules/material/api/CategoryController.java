package io.github.jameshome.galley.modules.material.api;

import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.CategoryCreate;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.CategoryPatch;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.CategoryView;
import io.github.jameshome.galley.modules.material.application.CategoryService;
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
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryView> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive).stream().map(CategoryView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@Valid @RequestBody CategoryCreate body) {
        int sort = body.sortOrder() == null ? 0 : body.sortOrder();
        return CategoryView.from(service.create(body.name(), sort));
    }

    @PatchMapping("/{id}")
    public CategoryView update(@PathVariable UUID id, @Valid @RequestBody CategoryPatch body) {
        return CategoryView.from(service.update(id, body.name(), body.sortOrder(), body.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
