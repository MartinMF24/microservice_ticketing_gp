package com.uade.microservices.ticketing.service;

import com.uade.microservices.ticketing.dto.response.ReservaEntradaResponseDto;
import com.uade.microservices.ticketing.model.EntradaGrada;
import com.uade.microservices.ticketing.model.EventoF1;
import com.uade.microservices.ticketing.model.GranPremioTarget;
import com.uade.microservices.ticketing.repository.EntradaGradaRepository;
import com.uade.microservices.ticketing.repository.EventoF1Repository;
import com.uade.microservices.ticketing.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio encargado de gestionar la reserva de entradas contra el sistema legado SOAP (F1 Ticketing).
 *
 * Flujo:
 * 1. Recibe el idEntrada (UUID de Supabase) y la cantidad requerida.
 * 2. Consulta en Supabase (en modo solo lectura) para identificar el nombre de la tribuna y el id_evento.
 * 3. Resuelve el código del evento del sistema legado (ej: "F1-2026-MAD").
 * 4. Invoca la operación SOAP reservarEntradas(...) modificando el stock en el sistema legado.
 * 5. NO MODIFICA la base de datos de Supabase, ya que la deducción de inventario en la base de datos
 *    es gestionada de forma autónoma por el backend principal.
 */
@Service
public class TicketingReservationService {

    private static final Logger log = LoggerFactory.getLogger(TicketingReservationService.class);

    private final EntradaGradaRepository entradaGradaRepository;
    private final EventoF1Repository eventoF1Repository;
    private final TicketingClientService ticketingClientService;

    public TicketingReservationService(
            EntradaGradaRepository entradaGradaRepository,
            EventoF1Repository eventoF1Repository,
            TicketingClientService ticketingClientService) {
        this.entradaGradaRepository = entradaGradaRepository;
        this.eventoF1Repository = eventoF1Repository;
        this.ticketingClientService = ticketingClientService;
    }

    /**
     * Ejecuta la reserva en el sistema SOAP a partir del UUID de Supabase de la entrada.
     *
     * @param idEntrada Identificador UUID de la entrada en public.entradas_gradas (Supabase).
     * @param cantidad  Cantidad de asientos solicitados (> 0).
     * @return DTO con la confirmación de la reserva y el código emitido por el servicio SOAP.
     */
    @Transactional(readOnly = true)
    public ReservaEntradaResponseDto reservar(UUID idEntrada, int cantidad) {
        if (idEntrada == null) {
            throw new IllegalArgumentException("El identificador de la entrada (idEntrada) no puede ser nulo.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a reservar debe ser mayor a 0 (recibido: " + cantidad + ").");
        }

        log.info("Procesando reserva SOAP para idEntrada={} con cantidad={}", idEntrada, cantidad);

        // 1. Buscar la entrada en Supabase para obtener la tribuna y el id_evento asociado
        EntradaGrada entrada = entradaGradaRepository.findById(idEntrada)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada", "id", idEntrada));

        String nombreTribuna = entrada.getNombreTribuna();
        UUID idEvento = entrada.getIdEvento();

        log.info("Entrada localizada en Supabase: tribuna='{}', idEvento={}, tipo='{}'",
                nombreTribuna, idEvento, entrada.getTipo());

        // 2. Resolver el código legado del evento F1 para el servicio SOAP (ej: "F1-2026-MAD")
        ResolvedEventInfo eventInfo = resolveCodigoEvento(idEvento);
        String codigoEvento = eventInfo.codigoEvento();
        String carrera = eventInfo.carrera();

        log.info("Evento resuelto para llamada SOAP: codigoEvento='{}', carrera='{}'", codigoEvento, carrera);

        // 3. Invocar al sistema SOAP para efectuar la reserva y descontar el stock en el sistema legado
        String codigoConfirmacion = ticketingClientService.reservarEntradas(codigoEvento, nombreTribuna, cantidad);

        log.info("Reserva exitosa en SOAP con confirmación='{}' para {} - '{}'. (Base de datos Supabase preservada sin cambios)",
                codigoConfirmacion, codigoEvento, nombreTribuna);

        // 4. Calcular importes informativos
        BigDecimal precioUnitario = entrada.getPrecioUsd() != null ? entrada.getPrecioUsd() : BigDecimal.ZERO;
        BigDecimal precioTotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));

        return new ReservaEntradaResponseDto(
                codigoConfirmacion,
                entrada.getIdEntrada(),
                entrada.getIdEvento(),
                codigoEvento,
                carrera,
                nombreTribuna,
                entrada.getTipo(),
                cantidad,
                precioUnitario,
                precioTotal,
                "SUCCESS",
                "Reserva confirmada exitosamente en el sistema de ticketing F1 (SOAP).",
                OffsetDateTime.now()
        );
    }

    /**
     * Resuelve el código legado del evento SOAP a partir del UUID registrado en Supabase.
     */
    private ResolvedEventInfo resolveCodigoEvento(UUID idEvento) {
        // A. Búsqueda directa en catálogo maestro por idEvento preconfigurado
        Optional<GranPremioTarget> targetByUuid = GranPremioTarget.fromEventoId(idEvento);
        if (targetByUuid.isPresent()) {
            return new ResolvedEventInfo(
                    targetByUuid.get().getCodigoEvento(),
                    targetByUuid.get().getNombreCarrera()
            );
        }

        // B. Búsqueda en base de datos para recuperar circuito, ciudad y temporada
        EventoF1 evento = eventoF1Repository.findByIdWithCircuitoAndCiudad(idEvento)
                .orElseThrow(() -> new ResourceNotFoundException("Evento F1", "id", idEvento));

        Optional<GranPremioTarget> targetByEntity = GranPremioTarget.fromEventoF1(evento);
        if (targetByEntity.isPresent()) {
            return new ResolvedEventInfo(
                    targetByEntity.get().getCodigoEvento(),
                    targetByEntity.get().getNombreCarrera()
            );
        }

        // C. Inferencia heurística si el evento no está en el catálogo oficial
        String ciudad = (evento.getCircuito() != null && evento.getCircuito().getCiudad() != null
                && evento.getCircuito().getCiudad().getNombre() != null)
                ? evento.getCircuito().getCiudad().getNombre()
                : "GP";
        int season = evento.getTemporada() != null ? evento.getTemporada() : 2026;
        String cityCode = ciudad.length() >= 3 ? ciudad.substring(0, 3).toUpperCase() : ciudad.toUpperCase();

        return new ResolvedEventInfo(String.format("F1-%d-%s", season, cityCode), ciudad);
    }

    private record ResolvedEventInfo(String codigoEvento, String carrera) {}
}
