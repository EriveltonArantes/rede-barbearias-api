package com.redebarbeariasapi.exception;

/** Regra de negocio violada (horario ocupado, estoque insuficiente...). Vira HTTP 409. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
