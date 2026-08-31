package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Jedyne miejsce, w ktorym powstaja salda uczestnika. Historia transakcji jest
 * zrodlem prawdy: {@code loyaltyPoints} i {@code lifetimePoints} sa z niej
 * przeliczane, a nie modyfikowane przyrostowo w kilku serwisach naraz.
 *
 * <p>Rozdzielenie obu licznikow jest celowe: biezace saldo maleje przy wymianie
 * punktow na kupon i przy wygasnieciu, dorobek ({@code lifetimePoints}) nie.
 * Poziom lojalnosciowy wyznacza dorobek, dzieki czemu klient nie traci statusu
 * za korzystanie z programu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerPointsService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Przelicza stany transakcji z dat, a nastepnie oba salda uczestnika.
     *
     * @return transakcje klienta z aktualnymi stanami, w kolejnosci chronologicznej
     */
    @Transactional
    public List<Transaction> refresh(Customer customer) {
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> transactions = transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(customer.getId());

        boolean changed = false;
        for (Transaction transaction : transactions) {
            TransactionState resolvedState = resolveState(transaction, now);
            if (transaction.getState() != resolvedState) {
                transaction.setState(resolvedState);
                changed = true;
            }
        }
        if (changed) {
            transactionRepository.saveAll(transactions);
        }

        customer.setLoyaltyPoints(sumAvailablePoints(transactions));
        customer.setLifetimePoints(sumLifetimePoints(transactions));
        customerRepository.save(customer);

        return transactions;
    }

    /**
     * Stan transakcji wyliczony z dat. Korekty reczne i operacje kuponowe nie maja
     * karencji ani daty wygasniecia -- ich punkty sa dostepne od razu i na stale.
     */
    public TransactionState resolveState(Transaction transaction, LocalDateTime now) {
        if (transaction.getType() != null && transaction.getType().isImmediatelyAvailable()) {
            return TransactionState.AVAILABLE;
        }
        if (now.isBefore(transaction.getAvailableFrom())) {
            return TransactionState.PENDING;
        }
        if (now.isAfter(transaction.getExpiresAt())) {
            return TransactionState.EXPIRED;
        }
        return TransactionState.AVAILABLE;
    }

    private int sumAvailablePoints(List<Transaction> transactions) {
        return transactions.stream()
                .filter(transaction -> transaction.getState() == TransactionState.AVAILABLE)
                .mapToInt(Transaction::getPoints)
                .sum();
    }

    /**
     * Dorobek nie zna stanu transakcji: punkty raz zdobyte pozostaja w dorobku takze
     * po wygasnieciu. Pomijane sa wylacznie operacje kuponowe -- patrz
     * {@code TransactionType#countsTowardsLifetimePoints()}.
     */
    private int sumLifetimePoints(List<Transaction> transactions) {
        int lifetime = transactions.stream()
                .filter(transaction -> transaction.getType() != null
                        && transaction.getType().countsTowardsLifetimePoints())
                .mapToInt(Transaction::getPoints)
                .sum();
        return Math.max(0, lifetime);
    }
}
