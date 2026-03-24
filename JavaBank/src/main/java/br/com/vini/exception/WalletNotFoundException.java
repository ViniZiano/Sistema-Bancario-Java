package br.com.vini.exception;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String message) {
        super(message);
    }

}
