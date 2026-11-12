package br.com.vini.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP do site e da API usando só o JDK (com.sun.net.httpserver), sem frameworks.
 * O site (HTML/CSS/JS em src/main/resources/static) e a API ficam na mesma origem.
 *
 * Execução: java -cp javabank.jar br.com.vini.web.WebServer   (porta pela variável PORT, padrão 8080)
 */
public final class WebServer {

    private static final int TAMANHO_MAXIMO_CORPO = 8 * 1024;
    private static final int MAX_SESSOES = 25;
    private static final long VALIDADE_SESSAO_MINUTOS = 30;

    // só estes arquivos são servidos: nada de caminho vindo do cliente chega ao sistema de arquivos
    private static final Map<String, String[]> ARQUIVOS = Map.of(
            "/", new String[]{"/static/index.html", "text/html; charset=utf-8"},
            "/index.html", new String[]{"/static/index.html", "text/html; charset=utf-8"},
            "/css/style.css", new String[]{"/static/css/style.css", "text/css; charset=utf-8"},
            "/js/app.js", new String[]{"/static/js/app.js", "text/javascript; charset=utf-8"},
            "/favicon.svg", new String[]{"/static/favicon.svg", "image/svg+xml"});

    private static final String CSP = "default-src 'self'; style-src 'self' https://fonts.googleapis.com; "
            + "font-src https://fonts.gstatic.com; img-src 'self' data:; base-uri 'none'; frame-ancestors 'none'";

    private final Api api = new Api(new SessionStore(MAX_SESSOES, VALIDADE_SESSAO_MINUTOS));
    private final HttpServer servidor;

    WebServer(int porta) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.createContext("/api/", this::tratarApi);
        servidor.createContext("/", this::tratarArquivo);
        servidor.setExecutor(Executors.newFixedThreadPool(8));
    }

    void iniciar() {
        servidor.start();
    }

    int porta() {
        return servidor.getAddress().getPort();
    }

    void parar() {
        servidor.stop(0);
    }

    public static void main(String[] args) throws IOException {
        int porta = 8080;
        String env = System.getenv("PORT");
        if (env != null && !env.isBlank()) {
            porta = Integer.parseInt(env.trim());
        }
        WebServer web = new WebServer(porta);
        web.iniciar();
        System.out.println("JavaBank no ar: http://localhost:" + web.porta());
    }

    // ------------------------------------------------------------------ API

    private void tratarApi(HttpExchange troca) throws IOException {
        try {
            byte[] corpo = lerCorpo(troca.getRequestBody());
            if (corpo.length > TAMANHO_MAXIMO_CORPO) {
                enviar(troca, 413, "application/json; charset=utf-8",
                        Json.write(BankFacade.mapa("erro", "Requisição grande demais.")).getBytes(StandardCharsets.UTF_8));
                return;
            }
            Api.Resposta r = api.tratar(
                    troca.getRequestMethod(),
                    troca.getRequestURI().getPath(),
                    troca.getRequestURI().getRawQuery(),
                    new String(corpo, StandardCharsets.UTF_8),
                    troca.getRequestHeaders().getFirst("X-Session"));
            troca.getResponseHeaders().set("Cache-Control", "no-store");
            enviar(troca, r.status(), "application/json; charset=utf-8", r.corpo().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            e.printStackTrace();
            enviar(troca, 500, "application/json; charset=utf-8",
                    "{\"erro\":\"Erro interno. Tente novamente.\"}".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] lerCorpo(InputStream in) throws IOException {
        return in.readNBytes(TAMANHO_MAXIMO_CORPO + 1);
    }

    // ------------------------------------------------------------------ site

    private void tratarArquivo(HttpExchange troca) throws IOException {
        String[] arquivo = ARQUIVOS.get(troca.getRequestURI().getPath());
        if (arquivo == null || !troca.getRequestMethod().equals("GET")) {
            enviar(troca, 404, "text/plain; charset=utf-8", "Não encontrado".getBytes(StandardCharsets.UTF_8));
            return;
        }
        try (InputStream in = WebServer.class.getResourceAsStream(arquivo[0])) {
            if (in == null) {
                enviar(troca, 404, "text/plain; charset=utf-8", "Não encontrado".getBytes(StandardCharsets.UTF_8));
                return;
            }
            troca.getResponseHeaders().set("Cache-Control", "no-cache");
            enviar(troca, 200, arquivo[1], in.readAllBytes());
        }
    }

    // ------------------------------------------------------------------ resposta

    private static void enviar(HttpExchange troca, int status, String tipo, byte[] corpo) throws IOException {
        var h = troca.getResponseHeaders();
        h.set("Content-Type", tipo);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", CSP);
        troca.sendResponseHeaders(status, corpo.length);
        try (OutputStream out = troca.getResponseBody()) {
            out.write(corpo);
        }
    }
}
