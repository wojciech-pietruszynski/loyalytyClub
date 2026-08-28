package pl.pietruszynski.loyaltyclub.api.ecom.dto;

public record EcomCustomerProfileDto(
        Long customerId,
        String customerNumber,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String country,
        Integer loyaltyPoints,
        String loyaltyTierCode,
        String referralCode
) {}
