package br.com.vini.web;

import br.com.vini.exception.AccountWithInvestmentException;
import br.com.vini.model.AccountWallet;
import br.com.vini.model.InvestmentWallet;
import br.com.vini.model.Investments;
import br.com.vini.repository.AccountRepository;
import br.com.vini.repository.InvestmentsRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Camada entre o site e as regras de negócio do banco (AccountRepository e InvestmentsRepository).
 * As regras continuam todas nos repositórios; aqui ficam só o que é da versão web:
 * validação da entrada, limites da demonstração, extrato legível e acesso sincronizado
 * (as listas dos repositórios não são thread-safe e o servidor atende várias requisições ao mesmo tempo).
 *
 * Todos os valores são em CENTAVOS (o domínio modela um objeto Money por centavo).
 */
final class BankFacade {

    // Limites: o domínio cria um objeto por centavo, então sem teto uma demo pública estouraria a memória.
    static final long MAX_OPERACAO = 50_000;     // R$ 500,00 por operação
    static final long MAX_TOTAL = 100_000;       // R$ 1.000,00 somando contas e investimentos
    static final int MAX_CONTAS = 5;
    static final int MAX_PRODUTOS = 5;
    static final int MAX_CHAVES_PIX = 3;
    static final int MAX_TAMANHO_PIX = 40;
    static final long TAXA_MAXIMA = 20;          // % por rodada de rendimento
    private static final int MAX_MOVIMENTOS = 100;

    private AccountRepository contas = new AccountRepository();
    private InvestmentsRepository investimentos = new InvestmentsRepository();
    private final Map<AccountWallet, List<Map<String, Object>>> movimentos = new IdentityHashMap<>();

    // ------------------------------------------------------------------ operações

    synchronized String criarConta(List<String> chaves, long valorInicial) {
        List<String> pix = validarChaves(chaves);
        if (valorInicial < 0 || valorInicial > MAX_OPERACAO) {
            throw new ApiException(400, "O valor inicial deve ficar entre R$ 0,00 e " + brl(MAX_OPERACAO) + ".");
        }
        if (contas.list().size() >= MAX_CONTAS) {
            throw new ApiException(422, "Limite da demonstração: no máximo " + MAX_CONTAS + " contas.");
        }
        garantirEspaco(valorInicial);

        AccountWallet conta = contas.create(pix, valorInicial);
        if (valorInicial > 0) {
            registrar(conta, "CONTA", "ABERTURA", "Abertura de conta", valorInicial, conta.getFunds());
        }
        return "Conta criada com a chave " + pix.getFirst() + ".";
    }

    synchronized String depositar(String pix, long valor) {
        validarValor(valor);
        AccountWallet conta = contas.findByPix(normalizar(pix));
        garantirEspaco(valor);
        contas.deposit(normalizar(pix), valor);
        registrar(conta, "CONTA", "DEPOSITO", "Depósito", valor, conta.getFunds());
        return "Depósito de " + brl(valor) + " realizado.";
    }

    synchronized String sacar(String pix, long valor) {
        validarValor(valor);
        contas.withdraw(normalizar(pix), valor);
        AccountWallet conta = contas.findByPix(normalizar(pix));
        registrar(conta, "CONTA", "SAQUE", "Saque", -valor, conta.getFunds());
        return "Saque de " + brl(valor) + " realizado.";
    }

    synchronized String transferir(String origem, String destino, long valor) {
        validarValor(valor);
        AccountWallet de = contas.findByPix(normalizar(origem));
        AccountWallet para = contas.findByPix(normalizar(destino));
        if (de == para) {
            throw new ApiException(400, "A conta de origem e a de destino precisam ser diferentes.");
        }
        contas.transferMoney(normalizar(origem), normalizar(destino), valor);
        registrar(de, "CONTA", "TRANSFERENCIA", "Pix enviado para " + normalizar(destino), -valor, de.getFunds());
        registrar(para, "CONTA", "TRANSFERENCIA", "Pix recebido de " + normalizar(origem), valor, para.getFunds());
        return "Transferência de " + brl(valor) + " realizada.";
    }

    synchronized String criarInvestimento(long taxa, long valorMinimo) {
        if (taxa < 1 || taxa > TAXA_MAXIMA) {
            throw new ApiException(400, "A taxa deve ficar entre 1% e " + TAXA_MAXIMA + "% por rodada.");
        }
        if (valorMinimo < 1 || valorMinimo > MAX_OPERACAO) {
            throw new ApiException(400, "O valor mínimo deve ficar entre R$ 0,01 e " + brl(MAX_OPERACAO) + ".");
        }
        if (investimentos.list().size() >= MAX_PRODUTOS) {
            throw new ApiException(422, "Limite da demonstração: no máximo " + MAX_PRODUTOS + " investimentos.");
        }
        Investments novo = investimentos.create(taxa, valorMinimo);
        return "Investimento #" + novo.id() + " criado (" + taxa + "% por rodada).";
    }

    synchronized String aderirInvestimento(String pix, long investimentoId) {
        AccountWallet conta = contas.findByPix(normalizar(pix));
        InvestmentWallet carteira;
        try {
            carteira = investimentos.initInvestment(conta, investimentoId);
        } catch (AccountWithInvestmentException e) {
            // a mensagem original imprime o objeto inteiro da conta; aqui usamos uma mensagem limpa
            throw new ApiException(409, "Esta conta já possui um investimento.");
        }
        long aplicado = carteira.getFunds();
        registrar(conta, "CONTA", "APLICACAO", "Adesão ao investimento #" + investimentoId, -aplicado, conta.getFunds());
        registrar(conta, "INVESTIMENTO", "APLICACAO", "Adesão ao investimento #" + investimentoId, aplicado, carteira.getFunds());
        return "Conta aderiu ao investimento #" + investimentoId + " com " + brl(aplicado) + ".";
    }

    synchronized String aportar(String pix, long valor) {
        validarValor(valor);
        InvestmentWallet carteira = investimentos.findWalletByAccountPix(normalizar(pix));
        AccountWallet conta = carteira.getAccount();
        if (conta.getFunds() < valor) {
            throw new ApiException(422, "Saldo da conta insuficiente para esse aporte.");
        }
        investimentos.deposit(normalizar(pix), valor);
        registrar(conta, "CONTA", "APLICACAO", "Aporte no investimento", -valor, conta.getFunds());
        registrar(conta, "INVESTIMENTO", "APLICACAO", "Aporte no investimento", valor, carteira.getFunds());
        return "Aporte de " + brl(valor) + " realizado.";
    }

    synchronized String resgatar(String pix, long valor) {
        validarValor(valor);
        InvestmentWallet carteira = investimentos.findWalletByAccountPix(normalizar(pix));
        AccountWallet conta = carteira.getAccount();
        investimentos.withdraw(normalizar(pix), valor);
        registrar(conta, "INVESTIMENTO", "RESGATE", "Resgate para a conta", -valor, carteira.getFunds());
        registrar(conta, "CONTA", "RESGATE", "Resgate de investimento", valor, conta.getFunds());
        return "Resgate de " + brl(valor) + " realizado.";
    }

    /** Aplica uma rodada de rendimento em todas as carteiras (equivale à opção 12 do menu do terminal). */
    synchronized String aplicarRendimento() {
        List<InvestmentWallet> carteiras = investimentos.listWallets();
        if (carteiras.isEmpty()) {
            throw new ApiException(409, "Nenhuma conta está investindo ainda. Adira a um investimento primeiro.");
        }
        long previsto = carteiras.stream().mapToLong(w -> w.getFunds() * w.getInvestments().tax() / 100).sum();
        garantirEspaco(previsto);

        Map<InvestmentWallet, Long> antes = new IdentityHashMap<>();
        carteiras.forEach(w -> antes.put(w, w.getFunds()));
        investimentos.updateAmount();

        long total = 0;
        for (InvestmentWallet w : carteiras) {
            long ganho = w.getFunds() - antes.get(w);
            total += ganho;
            if (ganho > 0) {
                registrar(w.getAccount(), "INVESTIMENTO", "RENDIMENTO",
                        "Rendimento do investimento #" + w.getInvestments().id() + " (" + w.getInvestments().tax() + "%)",
                        ganho, w.getFunds());
            }
        }
        return total > 0
                ? "Rendimento aplicado: " + brl(total) + " distribuídos."
                : "Rendimento aplicado, mas os saldos ainda são pequenos demais para render.";
    }

    synchronized String reiniciar() {
        contas = new AccountRepository();
        investimentos = new InvestmentsRepository();
        movimentos.clear();
        return "Demonstração reiniciada.";
    }

    // ------------------------------------------------------------------ consultas

    synchronized Map<String, Object> estado() {
        Map<AccountWallet, InvestmentWallet> carteiraDaConta = new IdentityHashMap<>();
        investimentos.listWallets().forEach(w -> carteiraDaConta.put(w.getAccount(), w));

        List<Object> listaContas = new ArrayList<>();
        long saldoContas = 0;
        for (AccountWallet conta : contas.list()) {
            saldoContas += conta.getFunds();
            InvestmentWallet w = carteiraDaConta.get(conta);
            listaContas.add(mapa(
                    "pix", List.copyOf(conta.getPix()),
                    "saldo", conta.getFunds(),
                    "investimento", w == null ? null : mapa(
                            "id", w.getInvestments().id(),
                            "taxa", w.getInvestments().tax(),
                            "saldo", w.getFunds())));
        }

        List<Object> listaProdutos = new ArrayList<>();
        for (Investments p : investimentos.list()) {
            listaProdutos.add(mapa("id", p.id(), "taxa", p.tax(), "valorMinimo", p.initialFounds()));
        }

        long totalInvestido = 0;
        List<Object> listaCarteiras = new ArrayList<>();
        for (InvestmentWallet w : investimentos.listWallets()) {
            totalInvestido += w.getFunds();
            listaCarteiras.add(mapa(
                    "pix", List.copyOf(w.getAccount().getPix()),
                    "investimentoId", w.getInvestments().id(),
                    "taxa", w.getInvestments().tax(),
                    "saldo", w.getFunds()));
        }

        return mapa(
                "contas", listaContas,
                "investimentos", listaProdutos,
                "carteiras", listaCarteiras,
                "resumo", mapa("saldoContas", saldoContas, "totalInvestido", totalInvestido, "total", saldoContas + totalInvestido),
                "limites", mapa(
                        "valorMaximoOperacao", MAX_OPERACAO,
                        "totalMaximo", MAX_TOTAL,
                        "maxContas", MAX_CONTAS,
                        "maxInvestimentos", MAX_PRODUTOS,
                        "taxaMaxima", TAXA_MAXIMA));
    }

    synchronized Map<String, Object> extrato(String pix) {
        AccountWallet conta = contas.findByPix(normalizar(pix));
        List<Map<String, Object>> lista = new ArrayList<>(movimentos.getOrDefault(conta, List.of()));
        Collections.reverse(lista); // mais recentes primeiro
        return mapa("pix", List.copyOf(conta.getPix()), "saldo", conta.getFunds(), "movimentos", lista);
    }

    // ------------------------------------------------------------------ apoio

    private void registrar(AccountWallet conta, String onde, String tipo, String descricao, long valor, long saldoApos) {
        List<Map<String, Object>> lista = movimentos.computeIfAbsent(conta, c -> new ArrayList<>());
        lista.add(mapa(
                "data", OffsetDateTime.now().toString(),
                "onde", onde,
                "tipo", tipo,
                "descricao", descricao,
                "valor", valor,
                "saldoApos", saldoApos));
        if (lista.size() > MAX_MOVIMENTOS) {
            lista.removeFirst();
        }
    }

    private long totalGuardado() {
        long total = 0;
        for (AccountWallet c : contas.list()) {
            total += c.getFunds();
        }
        for (InvestmentWallet w : investimentos.listWallets()) {
            total += w.getFunds();
        }
        return total;
    }

    private void garantirEspaco(long adicional) {
        if (totalGuardado() + adicional > MAX_TOTAL) {
            throw new ApiException(422, "Limite da demonstração: no máximo " + brl(MAX_TOTAL)
                    + " somando contas e investimentos. Reinicie a demonstração para começar de novo.");
        }
    }

    private static void validarValor(long valor) {
        if (valor < 1 || valor > MAX_OPERACAO) {
            throw new ApiException(400, "Informe um valor entre R$ 0,01 e " + brl(MAX_OPERACAO) + ".");
        }
    }

    private static String normalizar(String pix) {
        return pix == null ? "" : pix.trim();
    }

    private static List<String> validarChaves(List<String> chaves) {
        if (chaves == null || chaves.isEmpty()) {
            throw new ApiException(400, "Informe pelo menos uma chave Pix.");
        }
        if (chaves.size() > MAX_CHAVES_PIX) {
            throw new ApiException(400, "Use no máximo " + MAX_CHAVES_PIX + " chaves Pix por conta.");
        }
        List<String> limpas = new ArrayList<>();
        for (String chave : chaves) {
            String c = normalizar(chave);
            if (c.isEmpty() || c.length() > MAX_TAMANHO_PIX || c.chars().anyMatch(Character::isISOControl)) {
                throw new ApiException(400, "Cada chave Pix deve ter de 1 a " + MAX_TAMANHO_PIX + " caracteres.");
            }
            if (limpas.contains(c)) {
                throw new ApiException(400, "A chave Pix " + c + " foi repetida.");
            }
            limpas.add(c);
        }
        return limpas;
    }

    static String brl(long centavos) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", centavos / 100.0);
    }

    static Map<String, Object> mapa(Object... paresChaveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            m.put((String) paresChaveValor[i], paresChaveValor[i + 1]);
        }
        return m;
    }
}
