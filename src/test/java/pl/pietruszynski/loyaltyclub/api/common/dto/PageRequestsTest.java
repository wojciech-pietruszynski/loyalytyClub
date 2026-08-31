package pl.pietruszynski.loyaltyclub.api.common.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsTest {

    private static final Sort SORT = Sort.by("id");

    @Test
    void of_withoutParameters_shouldUseDefaults() {
        Pageable pageable = PageRequests.of(null, null, SORT);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(PageRequests.DEFAULT_PAGE_SIZE);
    }

    /**
     * Bez gornego limitu stronicowanie nie chroni serwera: klient mogl by poprosic
     * o cala kolekcje jednym zadaniem, czyli o to, przed czym ma zabezpieczac.
     */
    @Test
    void of_sizeAboveLimit_shouldReject() {
        assertThatThrownBy(() -> PageRequests.of(0, PageRequests.MAX_PAGE_SIZE + 1, SORT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not exceed");
    }

    @Test
    void of_negativePage_shouldReject() {
        assertThatThrownBy(() -> PageRequests.of(-1, 10, SORT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void of_zeroSize_shouldReject() {
        assertThatThrownBy(() -> PageRequests.of(0, 0, SORT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void pageResponse_shouldCarryPaginationMetadata() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2, SORT), 6);

        PageResponse<String> response = PageResponse.of(page, Function.identity());

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(6);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
    }
}
