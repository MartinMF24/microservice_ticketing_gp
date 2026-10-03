package com.uade.microservices.ticketing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO para la solicitud de reserva de una entrada de Fórmula 1 en el sistema legado SOAP.
 * Contiene el identificador UUID de la entrada en Supabase y la cantidad requerida.
 */
public record ReservaEntradaRequestDto(

        @NotNull(message = "El idEntrada es obligatorio.")
        UUID idEntrada,

        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 1, message = "La cantidad solicitada debe ser de al menos 1 entrada.")
        Integer cantidad
) {
}
