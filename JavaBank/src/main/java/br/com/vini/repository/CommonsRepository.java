package br.com.vini.repository;

import br.com.vini.exception.NoFundsEnoughException;
import br.com.vini.model.Money;
import br.com.vini.model.MoneyAudit;
import br.com.vini.model.Wallet;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static br.com.vini.model.BankService.ACCOUNT;

public final class CommonsRepository {

    private CommonsRepository() {
        // impede instanciação
    }

    public static void checkFundsForTransaction(final Wallet source, final long amount) {
        if (source.getFunds() < amount) {
            throw new NoFundsEnoughException("Sua conta não tem dinheiro suficiente para realizar essa transação");
        }
    }

    public static List<Money> generateMoney(final UUID transactionId, final long funds, final String description) {
        var history = new MoneyAudit(transactionId, ACCOUNT, description, OffsetDateTime.now());
        return Stream.generate(() -> new Money(history))
                .limit(funds)
                .toList();
    }

}
