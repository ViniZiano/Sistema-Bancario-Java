package br.com.vini.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercita as rotas de ponta a ponta (JSON -> Api -> BankFacade -> repositórios), sem abrir porta. */
class ApiTest {

    private static final String SESSAO = "teste-sessao-0123456789";
    private static final String ANA = "ana@email.com";
    private static final String BIA = "11999990000";

    private Api api;

    @BeforeEach
    void setUp() {
        api = new Api(new SessionStore(10, 30));
    }

    private Api.Resposta get(String caminho, String query) {
        return api.tratar("GET", caminho, query, "", SESSAO);
    }

    private Api.Resposta post(String caminho, String corpo) {
        return api.tratar("POST", caminho, null, corpo, SESSAO);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(Api.Resposta r) {
        return (Map<String, Object>) Json.parse(r.corpo());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> estado() {
        return json(get("/api/estado", null));
    }

    @SuppressWarnings("unchecked")
    private long saldoDaConta(String pix) {
        for (Object o : (List<Object>) estado().get("contas")) {
            Map<String, Object> conta = (Map<String, Object>) o;
            if (((List<Object>) conta.get("pix")).contains(pix)) {
                return (Long) conta.get("saldo");
            }
        }
        throw new AssertionError("conta não encontrada: " + pix);
    }

    private void criarConta(String pix, long valor) {
        assertEquals(200, post("/api/contas", "{\"pix\":[\"" + pix + "\"],\"valor\":" + valor + "}").status());
    }

    // ---------------------------------------------------------------- básico

    @Test
    @DisplayName("health responde ok sem precisar de sessão")
    void health() {
        Api.Resposta r = api.tratar("GET", "/api/health", null, "", null);

        assertEquals(200, r.status());
        assertEquals("ok", json(r).get("status"));
    }

    @Test
    @DisplayName("Sem o cabeçalho de sessão a API responde 400")
    void semSessao() {
        assertEquals(400, api.tratar("GET", "/api/estado", null, "", null).status());
    }

    @Test
    @DisplayName("Rota inexistente 404, método errado 405, JSON ruim 400")
    void erros() {
        assertEquals(404, post("/api/nada", "{}").status());
        assertEquals(405, get("/api/contas", null).status());
        assertEquals(405, post("/api/estado", "{}").status());
        assertEquals(400, post("/api/contas", "{isso nao e json").status());
        assertEquals(400, post("/api/contas", "[1,2]").status());
    }

    // ---------------------------------------------------------------- contas

    @Test
    @DisplayName("Criar conta com duas chaves Pix aparece no estado com o saldo inicial")
    void criarConta() {
        Api.Resposta r = post("/api/contas", "{\"pix\":[\"" + ANA + "\",\"" + BIA + "\"],\"valor\":10000}");

        assertEquals(200, r.status());
        assertEquals(10000L, saldoDaConta(ANA));
        assertEquals(10000L, saldoDaConta(BIA)); // as duas chaves apontam para a mesma conta
    }

    @Test
    @DisplayName("Chave Pix repetida em outra conta responde 409")
    void pixEmUso() {
        criarConta(ANA, 1000);

        assertEquals(409, post("/api/contas", "{\"pix\":[\"" + ANA + "\"],\"valor\":0}").status());
    }

    @Test
    @DisplayName("Valida chaves Pix: vazia, repetida na mesma conta e acima do máximo")
    void validaChaves() {
        assertEquals(400, post("/api/contas", "{\"pix\":[],\"valor\":0}").status());
        assertEquals(400, post("/api/contas", "{\"pix\":[\"a\",\"a\"],\"valor\":0}").status());
        assertEquals(400, post("/api/contas", "{\"pix\":[\"a\",\"b\",\"c\",\"d\"],\"valor\":0}").status());
        assertEquals(400, post("/api/contas", "{\"pix\":[\"" + "x".repeat(41) + "\"],\"valor\":0}").status());
        assertEquals(400, post("/api/contas", "{\"pix\":[1],\"valor\":0}").status());
    }

    @Test
    @DisplayName("Valor precisa ser inteiro em centavos, positivo e até o limite")
    void validaValores() {
        criarConta(ANA, 1000);

        assertEquals(400, post("/api/depositos", "{\"pix\":\"" + ANA + "\",\"valor\":10.5}").status());
        assertEquals(400, post("/api/depositos", "{\"pix\":\"" + ANA + "\",\"valor\":\"100\"}").status());
        assertEquals(400, post("/api/depositos", "{\"pix\":\"" + ANA + "\",\"valor\":0}").status());
        assertEquals(400, post("/api/depositos", "{\"pix\":\"" + ANA + "\",\"valor\":-5}").status());
        assertEquals(400, post("/api/depositos", "{\"pix\":\"" + ANA + "\",\"valor\":50001}").status());
        assertEquals(400, post("/api/depositos", "{\"valor\":100}").status());
        assertEquals(1000L, saldoDaConta(ANA));
    }

    @Test
    @DisplayName("Depositar e sacar mudam o saldo; sacar além do saldo responde 422")
    void depositarESacar() {
        criarConta(ANA, 1000);

        assertEquals(200, post("/api/depositos", "{\"pix\":\"" + ANA + "\",\"valor\":500}").status());
        assertEquals(1500L, saldoDaConta(ANA));

        assertEquals(200, post("/api/saques", "{\"pix\":\"" + ANA + "\",\"valor\":300}").status());
        assertEquals(1200L, saldoDaConta(ANA));

        assertEquals(422, post("/api/saques", "{\"pix\":\"" + ANA + "\",\"valor\":5000}").status());
        assertEquals(1200L, saldoDaConta(ANA));
    }

    @Test
    @DisplayName("Operar em conta que não existe responde 404")
    void contaInexistente() {
        assertEquals(404, post("/api/depositos", "{\"pix\":\"fantasma\",\"valor\":100}").status());
        assertEquals(404, post("/api/saques", "{\"pix\":\"fantasma\",\"valor\":100}").status());
        assertEquals(404, get("/api/extrato", "pix=fantasma").status());
    }

    @Test
    @DisplayName("Transferência move o dinheiro entre contas; sem saldo 422; para si mesma 400")
    void transferir() {
        criarConta(ANA, 2000);
        criarConta(BIA, 0);

        assertEquals(200, post("/api/transferencias",
                "{\"origem\":\"" + ANA + "\",\"destino\":\"" + BIA + "\",\"valor\":700}").status());
        assertEquals(1300L, saldoDaConta(ANA));
        assertEquals(700L, saldoDaConta(BIA));

        assertEquals(422, post("/api/transferencias",
                "{\"origem\":\"" + ANA + "\",\"destino\":\"" + BIA + "\",\"valor\":9000}").status());
        assertEquals(400, post("/api/transferencias",
                "{\"origem\":\"" + ANA + "\",\"destino\":\"" + ANA + "\",\"valor\":100}").status());
        assertEquals(404, post("/api/transferencias",
                "{\"origem\":\"" + ANA + "\",\"destino\":\"ninguem\",\"valor\":100}").status());
        assertEquals(1300L, saldoDaConta(ANA));
    }

    @Test
    @DisplayName("Extrato lista os movimentos, o mais recente primeiro, com sinal e saldo após")
    @SuppressWarnings("unchecked")
    void extrato() {
        criarConta(ANA, 1000);
        post("/api/depositos", "{\"pix\":\"" + ANA + "\",\"valor\":500}");
        post("/api/saques", "{\"pix\":\"" + ANA + "\",\"valor\":200}");

        Map<String, Object> extrato = json(get("/api/extrato", "pix=" + ANA.replace("@", "%40")));
        List<Object> movimentos = (List<Object>) extrato.get("movimentos");

        assertEquals(1300L, extrato.get("saldo"));
        assertEquals(3, movimentos.size());
        Map<String, Object> maisRecente = (Map<String, Object>) movimentos.get(0);
        assertEquals("SAQUE", maisRecente.get("tipo"));
        assertEquals(-200L, maisRecente.get("valor"));
        assertEquals(1300L, maisRecente.get("saldoApos"));
        assertEquals("ABERTURA", ((Map<String, Object>) movimentos.get(2)).get("tipo"));
    }

    // ---------------------------------------------------------------- investimentos

    @Test
    @DisplayName("Fluxo de investimento: criar produto, aderir, aportar, render e resgatar")
    @SuppressWarnings("unchecked")
    void fluxoDeInvestimento() {
        criarConta(ANA, 10000);

        assertEquals(200, post("/api/investimentos", "{\"taxa\":10,\"valorMinimo\":1000}").status());
        assertEquals(200, post("/api/carteiras", "{\"pix\":\"" + ANA + "\",\"investimentoId\":1}").status());
        assertEquals(9000L, saldoDaConta(ANA));      // 1000 saíram da conta para a carteira

        assertEquals(200, post("/api/rendimentos", "{}").status());   // 10% de 1000
        assertEquals(200, post("/api/aportes", "{\"pix\":\"" + ANA + "\",\"valor\":500}").status());
        assertEquals(8500L, saldoDaConta(ANA));

        List<Object> carteiras = (List<Object>) estado().get("carteiras");
        assertEquals(1, carteiras.size());
        assertEquals(1600L, ((Map<String, Object>) carteiras.get(0)).get("saldo")); // 1000 + 100 + 500

        assertEquals(200, post("/api/resgates", "{\"pix\":\"" + ANA + "\",\"valor\":600}").status());
        assertEquals(9100L, saldoDaConta(ANA));
        assertEquals(422, post("/api/resgates", "{\"pix\":\"" + ANA + "\",\"valor\":40000}").status());
    }

    @Test
    @DisplayName("Aderir duas vezes responde 409 com mensagem limpa")
    void aderirDuasVezes() {
        criarConta(ANA, 10000);
        post("/api/investimentos", "{\"taxa\":5,\"valorMinimo\":1000}");
        post("/api/carteiras", "{\"pix\":\"" + ANA + "\",\"investimentoId\":1}");

        Api.Resposta r = post("/api/carteiras", "{\"pix\":\"" + ANA + "\",\"investimentoId\":1}");

        assertEquals(409, r.status());
        assertEquals("Esta conta já possui um investimento.", json(r).get("erro"));
    }

    @Test
    @DisplayName("Investimento inexistente 404; saldo menor que o mínimo 422; taxa fora do limite 400")
    void errosDeInvestimento() {
        criarConta(ANA, 500);
        post("/api/investimentos", "{\"taxa\":5,\"valorMinimo\":1000}");

        assertEquals(404, post("/api/carteiras", "{\"pix\":\"" + ANA + "\",\"investimentoId\":99}").status());
        assertEquals(422, post("/api/carteiras", "{\"pix\":\"" + ANA + "\",\"investimentoId\":1}").status());
        assertEquals(400, post("/api/investimentos", "{\"taxa\":0,\"valorMinimo\":100}").status());
        assertEquals(400, post("/api/investimentos", "{\"taxa\":21,\"valorMinimo\":100}").status());
        assertEquals(400, post("/api/investimentos", "{\"taxa\":5,\"valorMinimo\":0}").status());
        assertEquals(404, post("/api/aportes", "{\"pix\":\"" + ANA + "\",\"valor\":100}").status());
    }

    @Test
    @DisplayName("Rendimento sem nenhuma carteira responde 409")
    void rendimentoSemCarteira() {
        assertEquals(409, post("/api/rendimentos", "{}").status());
    }

    // ---------------------------------------------------------------- limites e isolamento

    @Test
    @DisplayName("Limite total da demonstração (R$ 1.000,00) é respeitado")
    void limiteTotal() {
        criarConta(ANA, BankFacade.MAX_OPERACAO);
        criarConta(BIA, BankFacade.MAX_OPERACAO);

        assertEquals(422, post("/api/depositos", "{\"pix\":\"" + ANA + "\",\"valor\":1}").status());
        assertEquals(422, post("/api/contas", "{\"pix\":[\"nova\"],\"valor\":1}").status());
    }

    @Test
    @DisplayName("Limite de contas por sessão")
    void limiteDeContas() {
        for (int i = 0; i < BankFacade.MAX_CONTAS; i++) {
            criarConta("conta" + i, 0);
        }

        assertEquals(422, post("/api/contas", "{\"pix\":[\"extra\"],\"valor\":0}").status());
    }

    @Test
    @DisplayName("Reset zera tudo")
    @SuppressWarnings("unchecked")
    void reset() {
        criarConta(ANA, 1000);

        assertEquals(200, post("/api/reset", "{}").status());

        assertEquals(0, ((List<Object>) estado().get("contas")).size());
    }

    @Test
    @DisplayName("Cada sessão enxerga só o próprio banco")
    @SuppressWarnings("unchecked")
    void sessoesIsoladas() {
        criarConta(ANA, 1000);

        Api.Resposta deOutro = api.tratar("GET", "/api/estado", null, "", "outra-sessao-9876543210");

        assertEquals(0, ((List<Object>) json(deOutro).get("contas")).size());
        assertEquals(404, api.tratar("POST", "/api/depositos", null,
                "{\"pix\":\"" + ANA + "\",\"valor\":100}", "outra-sessao-9876543210").status());
    }
}
