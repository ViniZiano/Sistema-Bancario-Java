package br.com.vini.repository;

import br.com.vini.exception.AccountWithInvestmentException;
import br.com.vini.exception.InvestimentNotFoundException;
import br.com.vini.exception.WalletNotFoundException;
import br.com.vini.model.AccountWallet;
import br.com.vini.model.InvestmentWallet;
import br.com.vini.model.Investments;

import java.util.ArrayList;
import java.util.List;

import static br.com.vini.repository.CommonsRepository.checkFundsForTransaction;

public class InvestmentsRepository {

        private long nextId;
        private final List<Investments> investments = new ArrayList<>();
        private final List<InvestmentWallet> wallets = new ArrayList<>();

        public Investments create(final long tax, final long initialFunds){
                this.nextId++;
                var investment = new Investments(this.nextId, tax, initialFunds);
                investments.add(investment);
                return investment;
        }

        public InvestmentWallet initInvestment(final AccountWallet account, final long id){
                var accountInUse = wallets.stream()
                        .map(InvestmentWallet::getAccount)
                        .toList();

                if (accountInUse.contains(account)) {
                        throw new AccountWithInvestmentException("A conta " + account + " já possui um investimento");
                }

                var investment = findById(id);

                checkFundsForTransaction(account, investment.initialFounds());

                var wallet = new InvestmentWallet(investment, account, investment.initialFounds());
                wallets.add(wallet);
                return wallet;
        }

        public InvestmentWallet deposit(final String pix, final long funds){
                var wallet = findWalletByAccountPix(pix);

                wallet.addMoney(
                        wallet.getAccount().reduceMoney(funds),
                        wallet.getService(),
                        "Investimento"
                );

                return wallet;
        }

        public InvestmentWallet withdraw(final String pix, final long funds){
                var wallet = findWalletByAccountPix(pix);

                checkFundsForTransaction(wallet, funds);

                wallet.getAccount().addMoney(
                        wallet.reduceMoney(funds),
                        wallet.getService(),
                        "Saque de investimentos"
                );

                if (wallet.getFunds() == 0){
                        wallets.remove(wallet);
                }

                return wallet;
        }

        public void updateAmount() {
                wallets.forEach(w -> w.updateAmount(w.getInvestments().tax()));
        }

        public Investments findById(final long id){
                return investments.stream()
                        .filter(a -> a.id() == id)
                        .findFirst()
                        .orElseThrow(() ->
                                new InvestimentNotFoundException("O investimento " + id + " não foi encontrado.")
                        );
        }

        public InvestmentWallet findWalletByAccountPix(final String pix){
                return wallets.stream()
                        .filter(w -> w.getAccount().getPix().contains(pix))
                        .findFirst()
                        .orElseThrow(() ->
                                new WalletNotFoundException("A carteira não foi encontrada")
                        );
        }

        public List<InvestmentWallet> listWallets() {
                return this.wallets;
        }

        public List<Investments> list(){
                return this.investments;
        }

}
