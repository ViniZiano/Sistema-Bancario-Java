package br.com.vini;

import br.com.vini.exception.AccountNotFoundException;
import br.com.vini.exception.NoFundsEnoughException;
import br.com.vini.repository.AccountRepository;
import br.com.vini.repository.InvestmentsRepository;

import java.util.Arrays;
import java.util.Scanner;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

public class Main {

    private final static AccountRepository accountRepository = new AccountRepository();
    private final static InvestmentsRepository investmentsRepository = new InvestmentsRepository();

    static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("Olá, seja bem vindo ao Banco");

        while (true) {
            System.out.println("\nSelecione a operação desejada");
            System.out.println("1 - Criar uma Conta");
            System.out.println("2 - Criar um Investimento");
            System.out.println("3 - Fazer um Investimento");
            System.out.println("4 - Depositar na Conta");
            System.out.println("5 - Sacar da Conta");
            System.out.println("6 - Transferência entre Contas");
            System.out.println("7 - Investir ");
            System.out.println("8 - Sacar Investimento");
            System.out.println("9 - Listar Contas");
            System.out.println("10 - Listar Investimentos");
            System.out.println("11 - Listar Carteira de Investimento");
            System.out.println("12 - Atualizar Investimentos");
            System.out.println("13 - Histórico de Conta");
            System.out.println("14 - Sair ");

            var option = scanner.nextInt();
            scanner.nextLine(); // limpa buffer

            switch (option) {
                case 1:
                    createAccount();
                    break;
                case 2:
                    createInvestment();
                    break;
                case 3:
                    createWalletInvestment();
                    break;
                case 4:
                    deposit();
                    break;
                case 5:
                    withdraw();
                    break;
                case 6:
                    transferToAccount();
                    break;
                case 7:
                    incInvestment();
                    break;
                case 8:
                    rescueInvestment();
                    break;
                case 9:
                    accountRepository.list().forEach(System.out::println);
                    break;
                case 10:
                    investmentsRepository.list().forEach(System.out::println);
                    break;
                case 11:
                    investmentsRepository.listWallets().forEach(System.out::println);
                    break;
                case 12:
                    investmentsRepository.updateAmount();
                    System.out.println("Investimentos reajustados");
                    break;
                case 13:
                    checkHistory();
                    break;
                case 14:
                    System.exit(0);
                    break;
                default:
                    System.out.println("Opção inválida");
            }
        }
    }

    private static void createAccount() {
        System.out.println("Digite as chaves pix (separadas por ';')");
        var pix = Arrays.asList(scanner.nextLine().split(";"));

        System.out.println("Digite o valor inicial de depósito");
        var amount = scanner.nextLong();
        scanner.nextLine();

        var wallet = accountRepository.create(pix, amount);
        System.out.println("Conta criada: " + wallet);
    }

    private static void createInvestment() {
        System.out.println("Digite a taxa do investimento");
        var tax = scanner.nextInt();

        System.out.println("Digite o valor inicial de depósito");
        var initialFunds = scanner.nextLong();
        scanner.nextLine();

        investmentsRepository.create(tax, initialFunds);
        System.out.println("Investimento criado");
    }

    private static void withdraw() {
        System.out.println("Informe a chave da conta para saque");
        var pix = scanner.nextLine();

        System.out.println("Informe o valor a ser sacado");
        var amount = scanner.nextLong();
        scanner.nextLine();

        try {
            accountRepository.withdraw(pix, amount);
        } catch (NoFundsEnoughException | AccountNotFoundException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void deposit() {
        System.out.println("Informe a chave da conta para depósito");
        var pix = scanner.nextLine();

        System.out.println("Informe o valor a ser depositado");
        var amount = scanner.nextLong();
        scanner.nextLine();

        try {
            accountRepository.deposit(pix, amount);
        } catch (AccountNotFoundException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void transferToAccount() {
        System.out.println("Informe a chave da conta de origem");
        var source = scanner.nextLine();

        System.out.println("Informe a chave da conta de destino");
        var target = scanner.nextLine();

        System.out.println("Informe o valor da transferência");
        var amount = scanner.nextLong();
        scanner.nextLine();

        try {
            accountRepository.transferMoney(source, target, amount);
        } catch (AccountNotFoundException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void createWalletInvestment() {
        System.out.println("Informe a chave da conta");
        var pix = scanner.nextLine();

        var account = accountRepository.findByPix(pix);

        System.out.println("Informe o identificador do investimento");
        var investmentId = scanner.nextLong();
        scanner.nextLine();

        investmentsRepository.initInvestment(account, investmentId);
        System.out.println("Investimento criado");
    }

    private static void incInvestment() {
        System.out.println("Informe a chave da conta para investimento");
        var pix = scanner.nextLine();

        System.out.println("Informe o valor a ser investido");
        var amount = scanner.nextLong();
        scanner.nextLine();

        try {
            investmentsRepository.deposit(pix, amount);
        } catch (AccountNotFoundException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void rescueInvestment() {
        System.out.println("Informe a chave da conta para resgate do investimento");
        var pix = scanner.nextLine();

        System.out.println("Informe o valor a ser sacado");
        var amount = scanner.nextLong();
        scanner.nextLine();

        try {
            investmentsRepository.withdraw(pix, amount);
        } catch (NoFundsEnoughException | AccountNotFoundException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void checkHistory() {
        System.out.println("Informe a chave da conta para verificar o extrato");
        var pix = scanner.nextLine();

        try {
            var sortedHistory = accountRepository.getHistory(pix);

            sortedHistory.forEach((k, v) -> {
                System.out.println(k.format(ISO_DATE_TIME));
                System.out.println(v.getFirst().transactionId());
                System.out.println(v.getFirst().description());
                System.out.println(v.size());
            });

        } catch (AccountNotFoundException ex) {
            System.out.println(ex.getMessage());
        }
    }
}