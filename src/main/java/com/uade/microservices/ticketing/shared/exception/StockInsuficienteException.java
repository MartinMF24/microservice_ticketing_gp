package com.uade.microservices.ticketing.shared.exception;

/**
 * Excepción lanzada cuando el sistema legado SOAP rechaza la reserva por falta de stock
 * o disponibilidad insuficiente en la tribuna seleccionada.
 */
public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(String message) {
        super(message);
    }
}
