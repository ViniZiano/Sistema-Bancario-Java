package br.com.vini.model;

import lombok.Getter;

import java.util.List;

import static br.com.vini.model.BankService.ACCOUNT;

@Getter
public class AccountWallet extends Wallet {

    private final List<String> pix;

    public AccountWallet(final List<String> pix) {
        super(ACCOUNT);
        this.pix = pix;
    }

    public AccountWallet(final long amount, List<String> pix) {
        super(ACCOUNT);
        this.pix = pix;
        addMoney(amount, "Valor de criação da conta");
    }

    public void addMoney(final long amount, final String descricao) {
        var transactions = generateMoney(amount, descricao);
        this.money.addAll(transactions);
    }

    @Override
    public String toString() {
        return super.toString() + " AccountWallet{" +
                "pix=" + pix +
                '}';
    }

}
