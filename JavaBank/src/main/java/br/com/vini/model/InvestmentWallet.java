package br.com.vini.model;

import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static br.com.vini.model.BankService.INVESTMENT;

@Getter
public class InvestmentWallet extends Wallet{

    private final Investments investments;
    public final AccountWallet account;

    public InvestmentWallet(Investments investments, AccountWallet account, final long amount) {
        super(INVESTMENT);
        this.investments = investments;
        this.account = account;
        addMoney(account.reduceMoney(amount), getService(), "Investimento");
    }

    public void updateAmount(final long percent){
        var amount = getFunds() *  percent/100;
        var history = new MoneyAudit(UUID.randomUUID(), getService(), "Rendimentos", OffsetDateTime.now());
        var money = Stream.generate(() -> new Money(history)). limit(amount).toList();
        this.money .addAll(money);

    }

    @Override
    public String toString() {
        return super.toString()+ "InvestmentWallet{" +
                "investments=" + investments +
                ", account=" + account +
                '}';
    }
}
