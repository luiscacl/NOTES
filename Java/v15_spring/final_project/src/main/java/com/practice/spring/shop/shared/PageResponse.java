package com.practice.spring.shop.shared;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Una página como JSON estable. Devolver el Page de Spring Data directo expone su estructura
 * interna, que puede cambiar entre versiones (Spring Data lo advierte en el log).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
