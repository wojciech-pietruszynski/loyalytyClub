package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.LoyaltyTier;
import pl.pietruszynski.loyaltyclub.api.admin.repository.LoyaltyTierRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoyaltyTierService {

    private final LoyaltyTierRepository loyaltyTierRepository;

    /**
     * Poziom uczestnika. Liczony z dorobku punktowego, a nie z biezacego salda --
     * wymiana punktow na kupon ani ich wygasniecie nie moga obnizyc statusu.
     */
    public String resolveTierCode(Customer customer) {
        if (customer == null) {
            return null;
        }
        return resolveTierCode(customer.getLifetimePoints());
    }

    /**
     * Najwyzszy prog, ktory osiaga podana liczba punktow. Zwraca {@code null},
     * gdy wartosc jest nieznana albo nie zdefiniowano zadnego progu.
     */
    public String resolveTierCode(Integer points) {
        if (points == null) {
            return null;
        }
        String tierCode = null;
        for (LoyaltyTier tier : loyaltyTierRepository.findAllByOrderByMinPointsAsc()) {
            if (points >= tier.getMinPoints()) {
                tierCode = tier.getCode();
            }
        }
        return tierCode;
    }

    public List<LoyaltyTier> getTiers() {
        return loyaltyTierRepository.findAllByOrderByMinPointsAsc();
    }
}
