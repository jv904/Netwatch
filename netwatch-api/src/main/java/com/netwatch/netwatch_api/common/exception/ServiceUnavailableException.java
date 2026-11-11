package com.netwatch.netwatch_api.common.exception;

/** Recurso do sistema indisponível no momento (ex.: driver de captura ausente). Vira HTTP 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
