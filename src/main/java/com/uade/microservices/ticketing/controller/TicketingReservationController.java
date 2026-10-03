package com.uade.microservices.ticketing.controller;

import com.uade.microservices.ticketing.dto.request.ReservaEntradaRequestDto;
import com.uade.microservices.ticketing.dto.response.ReservaEntradaResponseDto;
import com.uade.microservices.ticketing.service.TicketingReservationService;
import com.uade.microservices.ticketing.shared.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la gestión de reservas de entradas en el sistema legado SOAP.
 *
 * Recibe el identificador UUID de la entrada en Supabase, resuelve la tribuna y el código
 * de evento de Fórmula 1, y ejecuta la reserva en el sistema SOAP descontando su stock
 * sin modificar la base de datos de Supabase.
 */
@RestController
@RequestMapping("/api/microservicios")
public class TicketingReservationController {

    private static final Logger log = LoggerFactory.getLogger(TicketingReservationController.class);

    private final TicketingReservationService ticketingReservationService;

    public TicketingReservationController(TicketingReservationService ticketingReservationService) {
        this.ticketingReservationService = ticketingReservationService;
    }

    /**
     * Endpoint principal para reservar entradas enviando el payload JSON.
     *
     * Ejemplo de cuerpo:
     * {
     *   "idEntrada": "55555555-0000-4000-8000-000000000003",
     *   "cantidad": 2
     * }
     */
    @PostMapping({"/tickets/reservar", "/sync-tickets/reservar", "/reservar-ticket"})
    public ResponseEntity<ApiResponse<ReservaEntradaResponseDto>> reservarEntradas(
            @Valid @RequestBody ReservaEntradaRequestDto request) {
        log.info("Petición REST de reserva recibida: idEntrada={}, cantidad={}",
                request.idEntrada(), request.cantidad());

        ReservaEntradaResponseDto response = ticketingReservationService.reservar(
                request.idEntrada(),
                request.cantidad()
        );

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Reserva confirmada en el sistema SOAP con código '%s'", response.codigoConfirmacion()),
                response
        ));
    }

    /**
     * Endpoint alternativo para reservar entradas mediante path variable y query param.
     *
     * Ejemplo: POST /api/microservicios/tickets/{idEntrada}/reservar?cantidad=2
     */
    @PostMapping("/tickets/{idEntrada}/reservar")
    public ResponseEntity<ApiResponse<ReservaEntradaResponseDto>> reservarEntradasPorRuta(
            @PathVariable("idEntrada") UUID idEntrada,
            @RequestParam(name = "cantidad", defaultValue = "1") int cantidad) {
        log.info("Petición REST de reserva recibida por ruta: idEntrada={}, cantidad={}", idEntrada, cantidad);

        ReservaEntradaResponseDto response = ticketingReservationService.reservar(idEntrada, cantidad);

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Reserva confirmada en el sistema SOAP con código '%s'", response.codigoConfirmacion()),
                response
        ));
    }
}
