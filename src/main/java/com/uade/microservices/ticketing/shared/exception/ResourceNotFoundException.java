package com.uade.microservices.ticketing.shared.exception;

/**
 * Excepción lanzada cuando una entidad o recurso solicitado no existe en la base de datos.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s no encontrado con %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
