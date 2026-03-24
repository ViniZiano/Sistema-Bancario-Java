package br.com.vini.model;

import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public abstract class Wallet {

    @Getter
    private final BankService service;

    protected final List<Money> money;

    public Wallet(BankService serviceType) {
        this.service = serviceType;
        this.money = new ArrayList<>();
    }

    protected List<Money> generateMoney(final long amount, final String description){
        var history = new MoneyAudit(UUID.randomUUID(), service, description, OffsetDateTime.now());
        return Stream.generate(() -> new Money(history))
                .limit(amount)
                .toList();
    }

    public long getFunds(){
        return money.size();
    }

    public void addMoney(final List<Money> money, final BankService service, final String description) {
        var history = new MoneyAudit(UUID.randomUUID(), service, description, OffsetDateTime.now());
        money.forEach(m -> m.addhistory(history));
        this.money.addAll(money);
    }

    public List<Money> reduceMoney(final long amount){
        if (amount > money.size()) {
            throw new RuntimeException("Saldo insuficiente");
        }

        List<Money> toRemove = new ArrayList<>();

        for (int i = 0; i < amount; i++) {
            toRemove.add(this.money.removeFirst()); // corrigido
        }

        return toRemove;
    }

    public List<MoneyAudit> getFinancialTransactions(){
        return money.stream()
                .flatMap(m -> m.getHistory().stream())
                .toList();
    }

    @Override
    public String toString() {
        return "Wallet{" +
                "service=" + service +
                ", money= R$" + (money.size() / 100.0) +
                '}';
    }

}
