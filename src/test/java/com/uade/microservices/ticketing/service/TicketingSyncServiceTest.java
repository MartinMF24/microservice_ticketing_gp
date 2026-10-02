package com.uade.microservices.ticketing.service;

import com.uade.microservices.ticketing.adapter.TicketingAdapter;
import com.uade.microservices.ticketing.dto.response.TicketingSyncResultDto;
import com.uade.microservices.ticketing.model.Circuito;
import com.uade.microservices.ticketing.model.Ciudad;
import com.uade.microservices.ticketing.model.EntradaGrada;
import com.uade.microservices.ticketing.model.EventoF1;
import com.uade.microservices.ticketing.repository.EntradaGradaRepository;
import com.uade.microservices.ticketing.repository.EventoF1Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import soap.ticketing.f1.legacy.InventarioGrada;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketingSyncServiceTest {

    @Mock
    private TicketingClientService ticketingClientService;

    @Spy
    private TicketingAdapter ticketingAdapter = new TicketingAdapter();

    @Mock
    private EntradaGradaRepository entradaGradaRepository;

    @Mock
    private EventoF1Repository eventoF1Repository;

    @InjectMocks
    private TicketingSyncService ticketingSyncService;

    private UUID mockEventoId;
    private EventoF1 mockEvento;

    @BeforeEach
    void setUp() {
        mockEventoId = UUID.randomUUID();

        Ciudad ciudad = new Ciudad(UUID.randomUUID(), "Madrid");
        Circuito circuito = new Circuito(UUID.randomUUID(), "Circuito de Madrid", ciudad.getIdCiudad());
        circuito.setCiudad(ciudad);

        mockEvento = new EventoF1(
                mockEventoId,
                2026,
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 13),
                "Proximo",
                circuito.getIdCircuito()
        );
        mockEvento.setCircuito(circuito);
    }

    @Test
    @DisplayName("Lógica Upsert: Si la entrada no existe en BD, debe insertarla (entradasCreadas = 1)")
    void shouldInsertWhenEntradaDoesNotExist() {
        String codigoEvento = "F1-2026-MAD";

        InventarioGrada gradaRaw = new InventarioGrada();
        gradaRaw.setCodigoEvento(codigoEvento);
        gradaRaw.setCarrera("Madrid");
        gradaRaw.setTribuna("Tribuna Principal");
        gradaRaw.setCategoria("Premium");
        gradaRaw.setPrecioUsd(new BigDecimal("1200.00"));
        gradaRaw.setStock(2500);

        when(ticketingClientService.extractEntradas(codigoEvento)).thenReturn(List.of(gradaRaw));
        when(eventoF1Repository.findByTemporadaWithCircuitoAndCiudad(2026)).thenReturn(List.of(mockEvento));
        when(entradaGradaRepository.findByIdEventoAndNombreTribunaIgnoreCase(eq(mockEventoId), eq("Tribuna Principal")))
                .thenReturn(Optional.empty());

        when(entradaGradaRepository.save(any(EntradaGrada.class))).thenAnswer(invocation -> {
            EntradaGrada e = invocation.getArgument(0);
            e.setIdEntrada(UUID.randomUUID());
            return e;
        });

        TicketingSyncResultDto result = ticketingSyncService.syncTicketsByEvent(codigoEvento);

        assertNotNull(result);
        assertEquals(1, result.entradasCreadas());
        assertEquals(0, result.entradasActualizadas());
        assertEquals("SUCCESS", result.estado());
        assertEquals(EntradaGrada.TIPO_ASIENTO_NUMERADO, result.entradas().get(0).tipo());

        verify(entradaGradaRepository, times(1)).save(any(EntradaGrada.class));
    }

    @Test
    @DisplayName("Lógica Upsert: Si la entrada ya existe en BD, debe actualizar precio y stock (entradasActualizadas = 1)")
    void shouldUpdateWhenEntradaAlreadyExists() {
        String codigoEvento = "F1-2026-MAD";

        InventarioGrada gradaRaw = new InventarioGrada();
        gradaRaw.setCodigoEvento(codigoEvento);
        gradaRaw.setCarrera("Madrid");
        gradaRaw.setTribuna("Tribuna Principal");
        gradaRaw.setCategoria("Premium");
        gradaRaw.setPrecioUsd(new BigDecimal("1350.00")); // Nuevo precio
        gradaRaw.setStock(1800);                          // Nuevo stock

        EntradaGrada entradaExistente = new EntradaGrada(
                mockEventoId,
                "Tribuna Principal",
                new BigDecimal("1200.00"),
                2500,
                "Asiento Numerado"
        );
        entradaExistente.setIdEntrada(UUID.randomUUID());

        when(ticketingClientService.extractEntradas(codigoEvento)).thenReturn(List.of(gradaRaw));
        when(eventoF1Repository.findByTemporadaWithCircuitoAndCiudad(2026)).thenReturn(List.of(mockEvento));
        when(entradaGradaRepository.findByIdEventoAndNombreTribunaIgnoreCase(eq(mockEventoId), eq("Tribuna Principal")))
                .thenReturn(Optional.of(entradaExistente));

        when(entradaGradaRepository.save(any(EntradaGrada.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketingSyncResultDto result = ticketingSyncService.syncTicketsByEvent(codigoEvento);

        assertNotNull(result);
        assertEquals(0, result.entradasCreadas());
        assertEquals(1, result.entradasActualizadas());
        assertEquals("SUCCESS", result.estado());

        // Verificar que los campos fueron actualizados en la entidad existente
        assertEquals(new BigDecimal("1350.00"), entradaExistente.getPrecioUsd());
        assertEquals(1800, entradaExistente.getStockDisponible());
    }

    @Test
    @DisplayName("Resolución de Evento: Debe resolver el UUID por coincidencia de nombre de ciudad/circuito en Supabase")
    void shouldResolveEventoIdFromDatabase() {
        when(eventoF1Repository.findByTemporadaWithCircuitoAndCiudad(2026)).thenReturn(List.of(mockEvento));

        UUID resolvedId = ticketingSyncService.resolveEventoId("F1-2026-MAD", List.of());

        assertEquals(mockEventoId, resolvedId);
        verify(eventoF1Repository, times(1)).findByTemporadaWithCircuitoAndCiudad(2026);
    }
}
