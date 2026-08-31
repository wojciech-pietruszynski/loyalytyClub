package pl.pietruszynski.loyaltyclub.api.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Koperta stronicowanej odpowiedzi.
 *
 * <p>Swiadomie nie zwracamy {@code org.springframework.data.domain.Page} wprost:
 * jego serializacja nie jest czescia kontraktu Spring Data i zmieniala sie miedzy
 * wersjami, a generatory klientow radza sobie z nia zle. Wlasny rekord daje stabilny
 * ksztalt odpowiedzi opisany w specyfikacji OpenAPI.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
