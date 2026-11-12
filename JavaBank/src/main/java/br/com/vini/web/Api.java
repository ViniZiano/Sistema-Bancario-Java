package br.com.vini.web;

import br.com.vini.exception.AccountNotFoundException;
import br.com.vini.exception.AccountWithInvestmentException;
import br.com.vini.exception.InvestimentNotFoundException;
import br.com.vini.exception.NoFundsEnoughException;
import br.com.vini.exception.PixInUseException;
import br.com.vini.exception.WalletNotFoundException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.vini.web.BankFacade.mapa;

/**
 * Rotas da API. Recebe método, caminho e corpo já como texto e devolve status + JSON,
 * sem depender do servidor HTTP (por isso dá para testar sem abrir porta).
 *
 * Rotas (o corpo e as respostas são JSON; valores em centavos; o cabeçalho X-Session é obrigatório):
 *   GET  /api/health
 *   GET  /api/estado
 *   GET  /api/extrato?pix=...
 *   POST /api/contas          {"pix":["a","b"], "valor":10000}
 *   POST /api/depositos       {"pix":"a", "valor":500}
 *   POST /api/saques          {"pix":"a", "valor":500}
 *   POST /api/transferencias  {"origem":"a", "destino":"b", "valor":500}
 *   POST /api/investimentos   {"taxa":5, "valorMinimo":1000}
 *   POST /api/carteiras       {"pix":"a", "investimentoId":1}
 *   POST /api/aportes         {"pix":"a", "valor":500}
 *   POST /api/resgates        {"pix":"a", "valor":500}
 *   POST /api/rendimentos
 *   POST /api/reset
 */
final class Api {

    record Resposta(int status, String corpo) {
    }

    private final SessionStore sessoes;

    Api(SessionStore sessoes) {
        this.sessoes = sessoes;
    }

    Resposta tratar(String metodo, String caminho, String query, String corpo, String sessao) {
        try {
            return new Resposta(200, Json.write(rotear(metodo, normalizarCaminho(caminho), query, corpo, sessao)));
        } catch (ApiException e) {
            return erro(e.status(), e.getMessage());
        } catch (AccountNotFoundException | WalletNotFoundException | InvestimentNotFoundException e) {
            return erro(404, e.getMessage());
        } catch (PixInUseException | AccountWithInvestmentException e) {
            return erro(409, e.getMessage());
        } catch (NoFundsEnoughException e) {
            return erro(422, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erro(400, e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            return erro(500, "Erro interno. Tente novamente.");
        }
    }

    private Object rotear(String metodo, String caminho, String query, String corpo, String sessao) {
        if (caminho.equals("/api/health")) {
            exigir(metodo, "GET");
            return mapa("status", "ok");
        }

        BankFacade banco = sessoes.obter(sessao);

        switch (caminho) {
            case "/api/estado" -> {
                exigir(metodo, "GET");
                return banco.estado();
            }
            case "/api/extrato" -> {
                exigir(metodo, "GET");
                return banco.extrato(parametro(query, "pix"));
            }
            default -> { /* segue para as operações (POST) */ }
        }

        Map<String, Object> dados = null;
        String mensagem;
        switch (caminho) {
            case "/api/contas" -> {
                dados = lerCorpo(metodo, corpo);
                mensagem = banco.criarConta(listaDeTextos(dados, "pix"), numero(dados, "valor"));
            }
            case "/api/depositos" -> {
                dados = lerCorpo(metodo, corpo);
                mensagem = banco.depositar(texto(dados, "pix"), numero(dados, "valor"));
            }
            case "/api/saques" -> {
                dados = lerCorpo(metodo, corpo);
                mensagem = banco.sacar(texto(dados, "pix"), numero(dados, "valor"));
            }
            case "/api/transferencias" -> {
                dados = lerCorpo(metodo, corpo);
                mensagem = banco.transferir(texto(dados, "origem"), texto(dados, "destino"), numero(dados, "valor"));
            }
            case "/api/investimentos" -> {
                dados = lerCorpo(metodo, corpo);
                mensagem = banco.criarInvestimento(numero(dados, "taxa"), numero(dados, "valorMinimo"));
            }
            case "/api/carteiras" -> {
                dados = lerCorpo(metodo, corpo);
                mensagem = banco.aderirInvestimento(texto(dados, "pix"), numero(dados, "investimentoId"));
            }
            case "/api/aportes" -> {
                dados = lerCorpo(metodo, corpo);
                mensagem = banco.aportar(texto(dados, "pix"), numero(dados, "valor"));
            }
            case "/api/resgates" -> {
                dados = lerCorpo(metodo, corpo);
                mensagem = banco.resgatar(texto(dados, "pix"), numero(dados, "valor"));
            }
            case "/api/rendimentos" -> {
                exigir(metodo, "POST");
                mensagem = banco.aplicarRendimento();
            }
            case "/api/reset" -> {
                exigir(metodo, "POST");
                mensagem = banco.reiniciar();
            }
            default -> throw new ApiException(404, "Rota não encontrada.");
        }
        return mapa("mensagem", mensagem, "estado", banco.estado());
    }

    // ------------------------------------------------------------------ leitura da requisição

    private static void exigir(String metodo, String esperado) {
        if (!esperado.equalsIgnoreCase(metodo)) {
            throw new ApiException(405, "Método não permitido. Use " + esperado + ".");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lerCorpo(String metodo, String corpo) {
        exigir(metodo, "POST");
        Object lido = Json.parse(corpo == null || corpo.isBlank() ? "{}" : corpo);
        if (!(lido instanceof Map)) {
            throw new ApiException(400, "O corpo da requisição deve ser um objeto JSON.");
        }
        return (Map<String, Object>) lido;
    }

    private static String texto(Map<String, Object> dados, String campo) {
        Object v = dados.get(campo);
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new ApiException(400, "Informe o campo \"" + campo + "\".");
    }

    private static long numero(Map<String, Object> dados, String campo) {
        Object v = dados.get(campo);
        if (v instanceof Long n) {
            return n;
        }
        throw new ApiException(400, "O campo \"" + campo + "\" deve ser um número inteiro.");
    }

    private static List<String> listaDeTextos(Map<String, Object> dados, String campo) {
        Object v = dados.get(campo);
        if (!(v instanceof List<?> itens)) {
            throw new ApiException(400, "O campo \"" + campo + "\" deve ser uma lista de textos.");
        }
        List<String> resultado = new ArrayList<>();
        for (Object item : itens) {
            if (!(item instanceof String s)) {
                throw new ApiException(400, "O campo \"" + campo + "\" deve ser uma lista de textos.");
            }
            resultado.add(s);
        }
        return resultado;
    }

    private static String parametro(String query, String nome) {
        if (query != null) {
            for (String par : query.split("&")) {
                int i = par.indexOf('=');
                if (i > 0 && par.substring(0, i).equals(nome)) {
                    return URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8);
                }
            }
        }
        throw new ApiException(400, "Informe o parâmetro \"" + nome + "\".");
    }

    private static String normalizarCaminho(String caminho) {
        if (caminho == null || caminho.isEmpty()) {
            return "/";
        }
        return caminho.length() > 1 && caminho.endsWith("/") ? caminho.substring(0, caminho.length() - 1) : caminho;
    }

    private static Resposta erro(int status, String mensagem) {
        return new Resposta(status, Json.write(mapa("erro", mensagem)));
    }
}
