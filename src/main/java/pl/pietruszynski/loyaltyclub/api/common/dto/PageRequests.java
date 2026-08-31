package pl.pietruszynski.loyaltyclub.api.common.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;

/**
 * Budowa zadania stronicowania z parametrow zapytania.
 *
 * <p>Gorny limit rozmiaru strony jest tu istotny: bez niego stronicowanie nie
 * chroni serwera, bo klient moze poprosic o cala kolekcje jednym zadaniem --
 * czyli dokladnie o to, przed czym stronicowanie ma zabezpieczac.
 */
public final class PageRequests {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 200;

    private PageRequests() {
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? DEFAULT_PAGE_SIZE : size;

        if (resolvedPage < 0) {
            throw new BusinessException("Page index must not be negative");
        }
        if (resolvedSize < 1) {
            throw new BusinessException("Page size must be greater than zero");
        }
        if (resolvedSize > MAX_PAGE_SIZE) {
            throw new BusinessException("Page size must not exceed " + MAX_PAGE_SIZE);
        }

        return PageRequest.of(resolvedPage, resolvedSize, sort);
    }
}
