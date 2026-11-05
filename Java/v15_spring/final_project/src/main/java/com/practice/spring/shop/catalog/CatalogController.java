package com.practice.spring.shop.catalog;

import com.practice.spring.shop.shared.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El catálogo por HTTP. Leer es público; crear y cambiar, solo ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/products")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    PageResponse<ProductResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return catalog.list(pageable);
    }

    @GetMapping("/{id}")
    ProductResponse find(@PathVariable long id) {
        return catalog.find(id);
    }

    @PostMapping
    ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = catalog.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
        return catalog.update(id, request);
    }
}
