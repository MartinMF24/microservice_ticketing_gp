package com.uade.microservices.ticketing.controller;

import com.uade.microservices.ticketing.dto.response.TicketingSyncAllSummaryDto;
import com.uade.microservices.ticketing.dto.response.TicketingSyncResultDto;
import com.uade.microservices.ticketing.model.GranPremioTarget;
import com.uade.microservices.ticketing.service.TicketingSyncService;
import com.uade.microservices.ticketing.shared.response.ApiResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la ejecución a demanda del módulo ETL de Ticketing (SOAP -> Supabase).
 * Provee endpoints individuales por código de evento (POST /{codigoEvento}) y masivos (POST /all).
 */
@RestController
@RequestMapping("/api/microservicios/sync-tickets")
public class TicketingSyncController {

    private static final Logger log = LoggerFactory.getLogger(TicketingSyncController.class);

    private final TicketingSyncService ticketingSyncService;

    public TicketingSyncController(TicketingSyncService ticketingSyncService) {
        this.ticketingSyncService = ticketingSyncService;
    }

    /**
     * Endpoint para ejecutar la sincronización ETL masiva de inventario de entradas y stock
     * desde el servicio SOAP para TODOS los eventos del catálogo de Fórmula 1.
     *
     * @return ApiResponse con el consolidado general y la lista de resultados por carrera.
     */
    @PostMapping({"/all", "/sync-all", ""})
    public ResponseEntity<ApiResponse<TicketingSyncAllSummaryDto>> syncAllTickets() {
        log.info("Petición recibida para sincronización masiva de entradas de TODOS los Grandes Premios");
        TicketingSyncAllSummaryDto summary = ticketingSyncService.syncAllTickets();
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Sincronización masiva de entradas completada: %d eventos exitosos de %d procesados (%d entradas creadas, %d actualizadas)",
                        summary.eventosExitosos(), summary.totalEventosProcesados(), summary.totalEntradasCreadas(), summary.totalEntradasActualizadas()),
                summary
        ));
    }

    /**
     * Endpoint para ejecutar la sincronización ETL de entradas para una carrera específica.
     *
     * @param codigoEvento Código del evento legado (ej: "F1-2026-MAD", "F1-2026-BAK", etc.)
     * @return ApiResponse con el detalle de entradas y stock sincronizados en Supabase.
     */
    @PostMapping("/{codigoEvento}")
    public ResponseEntity<?> syncTicketsByEvent(@PathVariable("codigoEvento") String codigoEvento) {
        log.info("Petición recibida para sincronización manual de entradas: codigoEvento={}", codigoEvento);

        if ("all".equalsIgnoreCase(codigoEvento) || "sync-all".equalsIgnoreCase(codigoEvento)) {
            return syncAllTickets();
        }

        TicketingSyncResultDto result = ticketingSyncService.syncTicketsByEvent(codigoEvento);

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Sincronización ETL de entradas completada exitosamente para %s (%s)",
                        result.codigoEvento(), result.carrera()),
                result
        ));
    }

    /**
     * Endpoint informativo auxiliar para listar todos los eventos de Gran Premio disponibles
     * en el catálogo maestro con sus códigos legados, fechas y sedes.
     */
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAvailableCatalog() {
        List<Map<String, Object>> catalog = Arrays.stream(GranPremioTarget.values())
                .map(target -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("target", target.name());
                    map.put("codigoEvento", target.getCodigoEvento());
                    map.put("carrera", target.getNombreCarrera());
                    map.put("temporada", target.getTemporada());
                    map.put("fechaCarrera", target.getFechaCarrera().toString());
                    map.put("eventoIdSupabase", target.getEventoId() != null ? target.getEventoId().toString() : "dinámico");
                    return map;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Catálogo maestro de eventos F1 disponibles para sincronización", catalog));
    }
}
