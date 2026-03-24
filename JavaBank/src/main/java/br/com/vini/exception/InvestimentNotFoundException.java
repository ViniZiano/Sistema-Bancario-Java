package br.com.vini.exception;

public class InvestimentNotFoundException extends RuntimeException {

    public InvestimentNotFoundException(String message) {
        super(message);
    }

}
