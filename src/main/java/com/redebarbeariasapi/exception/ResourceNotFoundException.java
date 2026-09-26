package com.redebarbeariasapi.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException de(String entidade, Object id) {
        return new ResourceNotFoundException(entidade + " não encontrado(a) (id " + id + ")");
    }
}
