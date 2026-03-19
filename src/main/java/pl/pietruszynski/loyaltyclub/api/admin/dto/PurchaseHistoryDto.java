package pl.pietruszynski.loyaltyclub.api.admin.dto;

import java.util.List;

public record PurchaseHistoryDto(List<PurchaseHistoryPointDto> points, int maxTotal) {
}
