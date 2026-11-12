package br.com.vini.web;

/** Erro com status HTTP, para a camada web responder com a mensagem certa. */
final class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
