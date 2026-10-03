package com.uade.microservices.ticketing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uade.microservices.ticketing.dto.response.ReservaEntradaResponseDto;
import com.uade.microservices.ticketing.service.TicketingReservationService;
import com.uade.microservices.ticketing.shared.exception.GlobalExceptionHandler;
import com.uade.microservices.ticketing.shared.exception.ResourceNotFoundException;
import com.uade.microservices.ticketing.shared.exception.StockInsuficienteException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

@ExtendWith(MockitoExtension.class)
class TicketingReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TicketingReservationService reservationService;

    @InjectMocks
    private TicketingReservationController reservationController;

    private final UUID idEntradaMock = UUID.fromString("55555555-0000-4000-8000-000000000003");
    private final UUID idEventoMock = UUID.fromString("992ae124-3d59-4adb-9fd2-f0e825c605e8");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/microservicios/tickets/reservar - Debe responder 200 OK con confirmación")
    void reservarEntradas_ConBody_Exitoso() throws Exception {
        ReservaEntradaResponseDto dto = new ReservaEntradaResponseDto(
                "TKT-99812",
                idEntradaMock,
                idEventoMock,
                "F1-2026-MAD",
                "Madrid",
                "Paddock Club Madrid",
                "VIP",
                2,
                new BigDecimal("3500.00"),
                new BigDecimal("7000.00"),
                "SUCCESS",
                "Reserva confirmada",
                OffsetDateTime.now()
        );

        when(reservationService.reservar(eq(idEntradaMock), eq(2))).thenReturn(dto);

        String jsonPayload = """
                {
                    "idEntrada": "55555555-0000-4000-8000-000000000003",
                    "cantidad": 2
                }
                """;

        mockMvc.perform(post("/api/microservicios/tickets/reservar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.codigoConfirmacion").value("TKT-99812"))
                .andExpect(jsonPath("$.data.cantidad").value(2))
                .andExpect(jsonPath("$.data.precioTotalUsd").value(7000.00))
                .andExpect(jsonPath("$.data.codigoEvento").value("F1-2026-MAD"));
    }

    @Test
    @DisplayName("POST /api/microservicios/tickets/{idEntrada}/reservar - Debe responder 200 OK")
    void reservarEntradas_ConRuta_Exitoso() throws Exception {
        ReservaEntradaResponseDto dto = new ReservaEntradaResponseDto(
                "TKT-11223",
                idEntradaMock,
                idEventoMock,
                "F1-2026-MAD",
                "Madrid",
                "Paddock Club Madrid",
                "VIP",
                1,
                new BigDecimal("3500.00"),
                new BigDecimal("3500.00"),
                "SUCCESS",
                "Reserva confirmada",
                OffsetDateTime.now()
        );

        when(reservationService.reservar(eq(idEntradaMock), eq(1))).thenReturn(dto);

        mockMvc.perform(post("/api/microservicios/tickets/" + idEntradaMock + "/reservar?cantidad=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.codigoConfirmacion").value("TKT-11223"));
    }

    @Test
    @DisplayName("POST /api/microservicios/tickets/reservar - Debe responder 404 si la entrada no existe")
    void reservarEntradas_EntradaNoEncontrada_404() throws Exception {
        when(reservationService.reservar(any(UUID.class), anyInt()))
                .thenThrow(new ResourceNotFoundException("Entrada", "id", idEntradaMock));

        String jsonPayload = """
                {
                    "idEntrada": "55555555-0000-4000-8000-000000000003",
                    "cantidad": 1
                }
                """;

        mockMvc.perform(post("/api/microservicios/tickets/reservar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no encontrado")));
    }

    @Test
    @DisplayName("POST /api/microservicios/tickets/reservar - Debe responder 409 si no hay stock suficiente en SOAP")
    void reservarEntradas_StockInsuficiente_409() throws Exception {
        when(reservationService.reservar(any(UUID.class), anyInt()))
                .thenThrow(new StockInsuficienteException("Stock insuficiente para la tribuna seleccionada"));

        String jsonPayload = """
                {
                    "idEntrada": "55555555-0000-4000-8000-000000000003",
                    "cantidad": 10
                }
                """;

        mockMvc.perform(post("/api/microservicios/tickets/reservar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Stock insuficiente para la tribuna seleccionada"));
    }

    @Test
    @DisplayName("POST /api/microservicios/tickets/reservar - Debe responder 400 Bad Request si la cantidad es menor a 1")
    void reservarEntradas_CantidadInvalida_400() throws Exception {
        String jsonPayload = """
                {
                    "idEntrada": "55555555-0000-4000-8000-000000000003",
                    "cantidad": 0
                }
                """;

        mockMvc.perform(post("/api/microservicios/tickets/reservar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
