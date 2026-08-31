package pl.pietruszynski.loyaltyclub.api.admin.dto;

import java.time.LocalDateTime;

/** Rozliczone polecenie widziane od strony polecajacego. */
public record ReferralRewardDto(
        Long id,
        Long referredCustomerId,
        String referredCustomerNumber,
        String referredCustomerName,
        Integer referrerPoints,
        Integer referredPoints,
        String country,
        LocalDateTime awardedAt
) {}
