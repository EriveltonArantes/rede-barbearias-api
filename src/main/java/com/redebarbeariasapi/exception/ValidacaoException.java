package com.redebarbeariasapi.exception;

/** Dado de entrada invalido que a Bean Validation nao pega sozinha. Vira HTTP 400. */
public class ValidacaoException extends RuntimeException {
    public ValidacaoException(String message) {
        super(message);
    }
}
