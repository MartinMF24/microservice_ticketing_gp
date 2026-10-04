package com.uade.microservices.ticketing.controller;

import com.uade.microservices.ticketing.shared.response.ApiResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador raíz del microservicio para verificación de estado y health-check.
 * Responde a peticiones GET / (ping de Render y navegadores) y GET /favicon.ico.
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<ApiResponse<Map<String, Object>>> root() {
        Map<String, Object> statusData = Map.of(
                "status", "UP",
                "service", "microservice_ticketing_gp",
                "version", "1.0.0",
                "timestamp", OffsetDateTime.now().toString(),
                "endpoints", List.of(
                        "POST /api/microservicios/tickets/reservar",
                        "POST /api/microservicios/sync-tickets/all",
                        "POST /api/microservicios/sync-tickets/{codigoEvento}"
                )
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Microservicio Ticketing GP activo y operativo.",
                statusData
        ));
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }
}
