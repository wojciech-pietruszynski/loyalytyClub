package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.LoyaltyTier;
import pl.pietruszynski.loyaltyclub.api.admin.repository.LoyaltyTierRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoyaltyTierService {

    private final LoyaltyTierRepository loyaltyTierRepository;

    /**
     * Najwyzszy prog, ktory saldo punktow osiaga. Zwraca {@code null},
     * gdy saldo jest nieznane albo nie zdefiniowano zadnego progu.
     */
    public String resolveTierCode(Integer loyaltyPoints) {
        if (loyaltyPoints == null) {
            return null;
        }
        List<LoyaltyTier> tiers = loyaltyTierRepository.findAllByOrderByMinPointsAsc();
        String tierCode = null;
        for (LoyaltyTier tier : tiers) {
            if (loyaltyPoints >= tier.getMinPoints()) {
                tierCode = tier.getCode();
            }
        }
        return tierCode;
    }

    public List<LoyaltyTier> getTiers() {
        return loyaltyTierRepository.findAllByOrderByMinPointsAsc();
    }
}
