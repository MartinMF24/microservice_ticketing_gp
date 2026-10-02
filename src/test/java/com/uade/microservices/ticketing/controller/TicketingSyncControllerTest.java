package com.uade.microservices.ticketing.controller;

import com.uade.microservices.ticketing.dto.response.EntradaSyncSummaryDto;
import com.uade.microservices.ticketing.dto.response.TicketingSyncAllSummaryDto;
import com.uade.microservices.ticketing.dto.response.TicketingSyncResultDto;
import com.uade.microservices.ticketing.service.TicketingSyncService;
import com.uade.microservices.ticketing.shared.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TicketingSyncControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TicketingSyncService ticketingSyncService;

    @InjectMocks
    private TicketingSyncController ticketingSyncController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ticketingSyncController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/microservicios/sync-tickets/{codigoEvento} debe retornar 200 OK y resultado de sincronización")
    void testSyncTicketsByEvent_Success() throws Exception {
        String codigo = "F1-2026-MAD";
        UUID idEvento = UUID.randomUUID();

        EntradaSyncSummaryDto entradaDto = new EntradaSyncSummaryDto(
                UUID.randomUUID(),
                idEvento,
                "Tribuna Principal",
                new BigDecimal("1200.00"),
                2500,
                "Asiento Numerado"
        );

        TicketingSyncResultDto mockResult = new TicketingSyncResultDto(
                codigo,
                idEvento,
                "Madrid",
                2026,
                LocalDate.of(2026, 9, 11),
                1,
                1,
                0,
                "SUCCESS",
                "Sincronización completada exitosamente",
                OffsetDateTime.now(),
                List.of(entradaDto)
        );

        when(ticketingSyncService.syncTicketsByEvent(eq(codigo))).thenReturn(mockResult);

        mockMvc.perform(post("/api/microservicios/sync-tickets/F1-2026-MAD")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.codigoEvento").value("F1-2026-MAD"))
                .andExpect(jsonPath("$.data.carrera").value("Madrid"))
                .andExpect(jsonPath("$.data.temporada").value(2026))
                .andExpect(jsonPath("$.data.entradasCreadas").value(1))
                .andExpect(jsonPath("$.data.entradas[0].nombreTribuna").value("Tribuna Principal"))
                .andExpect(jsonPath("$.data.entradas[0].tipo").value("Asiento Numerado"));

        verify(ticketingSyncService, times(1)).syncTicketsByEvent(codigo);
    }

    @Test
    @DisplayName("POST /api/microservicios/sync-tickets/all debe ejecutar la sincronización masiva y retornar resumen")
    void testSyncAllTickets_Success() throws Exception {
        TicketingSyncAllSummaryDto mockSummary = new TicketingSyncAllSummaryDto(
                19, 19, 0, 76, 50, 26, OffsetDateTime.now(), List.of()
        );

        when(ticketingSyncService.syncAllTickets()).thenReturn(mockSummary);

        mockMvc.perform(post("/api/microservicios/sync-tickets/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalEventosProcesados").value(19))
                .andExpect(jsonPath("$.data.eventosExitosos").value(19))
                .andExpect(jsonPath("$.data.totalEntradasCreadas").value(50))
                .andExpect(jsonPath("$.data.totalEntradasActualizadas").value(26));

        verify(ticketingSyncService, times(1)).syncAllTickets();
    }

    @Test
    @DisplayName("GET /api/microservicios/sync-tickets/catalog debe retornar el catálogo maestro de 19 eventos")
    void testListAvailableCatalog() throws Exception {
        mockMvc.perform(get("/api/microservicios/sync-tickets/catalog")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(19))
                .andExpect(jsonPath("$.data[0].codigoEvento").value("F1-2026-MAD"))
                .andExpect(jsonPath("$.data[0].carrera").value("Madrid"));
    }
}
