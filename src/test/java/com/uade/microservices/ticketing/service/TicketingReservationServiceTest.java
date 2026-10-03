package com.uade.microservices.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uade.microservices.ticketing.dto.response.ReservaEntradaResponseDto;
import com.uade.microservices.ticketing.model.Circuito;
import com.uade.microservices.ticketing.model.Ciudad;
import com.uade.microservices.ticketing.model.EntradaGrada;
import com.uade.microservices.ticketing.model.EventoF1;
import com.uade.microservices.ticketing.model.GranPremioTarget;
import com.uade.microservices.ticketing.repository.EntradaGradaRepository;
import com.uade.microservices.ticketing.repository.EventoF1Repository;
import com.uade.microservices.ticketing.shared.exception.ResourceNotFoundException;
import com.uade.microservices.ticketing.shared.exception.StockInsuficienteException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketingReservationServiceTest {

    @Mock
    private EntradaGradaRepository entradaGradaRepository;

    @Mock
    private EventoF1Repository eventoF1Repository;

    @Mock
    private TicketingClientService ticketingClientService;

    private TicketingReservationService reservationService;

    private final UUID idEntradaMock = UUID.fromString("55555555-0000-4000-8000-000000000003");
    private final UUID idEventoMadridMock = GranPremioTarget.MADRID.getEventoId();

    @BeforeEach
    void setUp() {
        reservationService = new TicketingReservationService(
                entradaGradaRepository,
                eventoF1Repository,
                ticketingClientService
        );
    }

    private EntradaGrada crearEntradaMock(UUID idEntrada, UUID idEvento, String tribuna, BigDecimal precio, int stock, String tipo) {
        EntradaGrada entrada = new EntradaGrada(idEvento, tribuna, precio, stock, tipo);
        entrada.setIdEntrada(idEntrada);
        return entrada;
    }

    @Test
    @DisplayName("Debe reservar exitosamente en SOAP y NO modificar la BD de Supabase")
    void reservar_Exitoso() {
        // Arrange
        EntradaGrada entrada = crearEntradaMock(
                idEntradaMock,
                idEventoMadridMock,
                "Paddock Club Madrid",
                new BigDecimal("3500.00"),
                500,
                "VIP"
        );
        when(entradaGradaRepository.findById(idEntradaMock)).thenReturn(Optional.of(entrada));
        when(ticketingClientService.reservarEntradas("F1-2026-MAD", "Paddock Club Madrid", 2))
                .thenReturn("TKT-99812");

        // Act
        ReservaEntradaResponseDto response = reservationService.reservar(idEntradaMock, 2);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.codigoConfirmacion()).isEqualTo("TKT-99812");
        assertThat(response.idEntrada()).isEqualTo(idEntradaMock);
        assertThat(response.idEvento()).isEqualTo(idEventoMadridMock);
        assertThat(response.codigoEvento()).isEqualTo("F1-2026-MAD");
        assertThat(response.carrera()).isEqualTo("Madrid");
        assertThat(response.nombreTribuna()).isEqualTo("Paddock Club Madrid");
        assertThat(response.cantidad()).isEqualTo(2);
        assertThat(response.precioUnitarioUsd()).isEqualByComparingTo("3500.00");
        assertThat(response.precioTotalUsd()).isEqualByComparingTo("7000.00");
        assertThat(response.estado()).isEqualTo("SUCCESS");

        // Verificación crítica: NUNCA se invoca save o modificación sobre entradaGradaRepository
        verify(entradaGradaRepository, never()).save(any());
        verify(entradaGradaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si la entrada no existe en Supabase")
    void reservar_EntradaNoEncontrada() {
        // Arrange
        UUID idDesconocido = UUID.randomUUID();
        when(entradaGradaRepository.findById(idDesconocido)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> reservationService.reservar(idDesconocido, 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Entrada no encontrado con id");

        verify(ticketingClientService, never()).reservarEntradas(anyString(), anyString(), anyInt());
        verify(entradaGradaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe lanzar IllegalArgumentException si la cantidad es menor o igual a 0")
    void reservar_CantidadInvalida() {
        assertThatThrownBy(() -> reservationService.reservar(idEntradaMock, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La cantidad a reservar debe ser mayor a 0");

        assertThatThrownBy(() -> reservationService.reservar(idEntradaMock, -3))
                .isInstanceOf(IllegalArgumentException.class);

        verify(entradaGradaRepository, never()).findById(any());
        verify(ticketingClientService, never()).reservarEntradas(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Debe propagar StockInsuficienteException si el servicio SOAP rechaza por stock y no alterar la BD")
    void reservar_StockInsuficienteEnSoap() {
        // Arrange
        EntradaGrada entrada = crearEntradaMock(
                idEntradaMock,
                idEventoMadridMock,
                "Paddock Club Madrid",
                new BigDecimal("3500.00"),
                1,
                "VIP"
        );
        when(entradaGradaRepository.findById(idEntradaMock)).thenReturn(Optional.of(entrada));
        when(ticketingClientService.reservarEntradas("F1-2026-MAD", "Paddock Club Madrid", 5))
                .thenThrow(new StockInsuficienteException("Stock insuficiente para la tribuna 'Paddock Club Madrid'"));

        // Act & Assert
        assertThatThrownBy(() -> reservationService.reservar(idEntradaMock, 5))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("Stock insuficiente");

        // Verificación de integridad: nada se modifica en la BD
        verify(entradaGradaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe resolver el código de evento SOAP consultando la BD cuando el UUID no está en el enum")
    void reservar_EventoResueltoDesdeBaseDeDatos() {
        // Arrange
        UUID idEventoDinamico = UUID.randomUUID();
        EntradaGrada entrada = crearEntradaMock(
                idEntradaMock,
                idEventoDinamico,
                "Tribuna Principal",
                new BigDecimal("1200.00"),
                100,
                "Asiento Numerado"
        );
        when(entradaGradaRepository.findById(idEntradaMock)).thenReturn(Optional.of(entrada));

        Ciudad ciudad = new Ciudad(UUID.randomUUID(), "Madrid");
        Circuito circuito = new Circuito(UUID.randomUUID(), "Circuito IFEMA", ciudad.getIdCiudad());
        circuito.setCiudad(ciudad);
        EventoF1 eventoF1 = new EventoF1(idEventoDinamico, 2026, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 13), "Activo", circuito.getIdCircuito());
        eventoF1.setCircuito(circuito);

        when(eventoF1Repository.findByIdWithCircuitoAndCiudad(idEventoDinamico)).thenReturn(Optional.of(eventoF1));
        when(ticketingClientService.reservarEntradas("F1-2026-MAD", "Tribuna Principal", 1))
                .thenReturn("TKT-12345");

        // Act
        ReservaEntradaResponseDto response = reservationService.reservar(idEntradaMock, 1);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.codigoConfirmacion()).isEqualTo("TKT-12345");
        assertThat(response.codigoEvento()).isEqualTo("F1-2026-MAD");
        verify(entradaGradaRepository, never()).save(any());
    }
}
