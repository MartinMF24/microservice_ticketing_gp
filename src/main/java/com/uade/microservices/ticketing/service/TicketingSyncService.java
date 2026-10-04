package com.uade.microservices.ticketing.service;

import com.uade.microservices.ticketing.adapter.TicketingAdapter;
import com.uade.microservices.ticketing.dto.response.EntradaSyncSummaryDto;
import com.uade.microservices.ticketing.dto.response.TicketingSyncAllSummaryDto;
import com.uade.microservices.ticketing.dto.response.TicketingSyncResultDto;
import com.uade.microservices.ticketing.model.EntradaGrada;
import com.uade.microservices.ticketing.model.EventoF1;
import com.uade.microservices.ticketing.model.GranPremioTarget;
import com.uade.microservices.ticketing.repository.EntradaGradaRepository;
import com.uade.microservices.ticketing.repository.EventoF1Repository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soap.ticketing.f1.legacy.InventarioGrada;

/**
 * Servicio orquestador del módulo ETL de Entradas F1 Ticketing (Extract, Transform, Load).
 * Contiene la lógica de resolución del evento F1 en Supabase a partir de la temporada y la ciudad/circuito,
 * y aplica la operación atómica de Upsert sobre la tabla public.entradas_gradas.
 */
@Service
public class TicketingSyncService {

    private static final Logger log = LoggerFactory.getLogger(TicketingSyncService.class);

    private final TicketingClientService ticketingClientService;
    private final TicketingAdapter ticketingAdapter;
    private final EntradaGradaRepository entradaGradaRepository;
    private final EventoF1Repository eventoF1Repository;

    public TicketingSyncService(
            TicketingClientService ticketingClientService,
            TicketingAdapter ticketingAdapter,
            EntradaGradaRepository entradaGradaRepository,
            EventoF1Repository eventoF1Repository
    ) {
        this.ticketingClientService = ticketingClientService;
        this.ticketingAdapter = ticketingAdapter;
        this.entradaGradaRepository = entradaGradaRepository;
        this.eventoF1Repository = eventoF1Repository;
    }

    /**
     * Sincronización masiva de entradas para todos los Grandes Premios configurados en el catálogo.
     * Incorpora aislamiento de fallos por evento: si un Gran Premio falla, se registra el error
     * y el proceso continúa con los restantes.
     *
     * @return Consolidado completo con métricas y lista de resultados detallados.
     */
    public TicketingSyncAllSummaryDto syncAllTickets() {
        log.info("Iniciando sincronización masiva de entradas SOAP para todos los Grandes Premios...");

        List<TicketingSyncResultDto> resultados = new ArrayList<>();
        int totalEntradasExtraidas = 0;
        int totalEntradasCreadas = 0;
        int totalEntradasActualizadas = 0;
        int eventosExitosos = 0;
        int eventosConError = 0;

        GranPremioTarget[] targets = GranPremioTarget.values();

        for (GranPremioTarget target : targets) {
            String codigo = target.getCodigoEvento();
            try {
                log.info("Sincronizando evento masivo: {} ({})", codigo, target.getNombreCarrera());
                TicketingSyncResultDto result = syncTicketsByEvent(codigo);
                resultados.add(result);

                totalEntradasExtraidas += result.totalEntradasExtraidas();
                totalEntradasCreadas += result.entradasCreadas();
                totalEntradasActualizadas += result.entradasActualizadas();

                if ("SUCCESS".equalsIgnoreCase(result.estado())) {
                    eventosExitosos++;
                } else {
                    eventosConError++;
                }
            } catch (Exception ex) {
                eventosConError++;
                log.error("Error al sincronizar entradas para el evento {}: {}", codigo, ex.getMessage(), ex);
                resultados.add(new TicketingSyncResultDto(
                        codigo,
                        target.getEventoId(),
                        target.getNombreCarrera(),
                        target.getTemporada(),
                        target.getFechaCarrera(),
                        0, 0, 0,
                        "ERROR",
                        "Error al sincronizar: " + ex.getMessage(),
                        OffsetDateTime.now(),
                        Collections.emptyList()
                ));
            }
        }

        log.info("Sincronización masiva finalizada. Eventos procesados: {}, Exitosos: {}, Con error: {}, Entradas creadas: {}, Entradas actualizadas: {}",
                targets.length, eventosExitosos, eventosConError, totalEntradasCreadas, totalEntradasActualizadas);

        return new TicketingSyncAllSummaryDto(
                targets.length,
                eventosExitosos,
                eventosConError,
                totalEntradasExtraidas,
                totalEntradasCreadas,
                totalEntradasActualizadas,
                OffsetDateTime.now(),
                resultados
        );
    }

    /**
     * Ejecuta el pipeline ETL para un evento específico a partir de su código legado (ej: "F1-2026-MAD"):
     * 1. EXTRACT: Consume la operación SOAP consultarDisponibilidad.
     * 2. RESOLVE: Busca el id_evento (UUID) en Supabase utilizando el año y la ciudad/circuito derivados del código.
     * 3. TRANSFORM: Aplica el Adapter y la regla de mapeo CHECK ('VIP', 'Asiento Numerado', 'General').
     * 4. LOAD (UPSERT): Si ya existe una entrada para ese id_evento y nombre_tribuna, actualiza precio_usd
     *    y stock_disponible. Si no existe, la inserta.
     *
     * @param codigoEvento Código legado del evento (ej: "F1-2026-MAD").
     * @return DTO con el resultado de la operación.
     */
    @Transactional
    public TicketingSyncResultDto syncTicketsByEvent(String codigoEvento) {
        if (codigoEvento == null || codigoEvento.isBlank()) {
            throw new IllegalArgumentException("El código de evento es obligatorio.");
        }

        String codigoNormalizado = codigoEvento.trim().toUpperCase();
        log.info("Iniciando proceso ETL de entradas para codigoEvento: {}", codigoNormalizado);

        // 1. EXTRACT: Consulta al servicio SOAP
        List<InventarioGrada> gradasRaw = ticketingClientService.extractEntradas(codigoNormalizado);

        if (gradasRaw.isEmpty()) {
            log.warn("No se encontraron gradas en el sistema SOAP para {}", codigoNormalizado);
            Optional<GranPremioTarget> targetOpt = GranPremioTarget.fromCodigoEvento(codigoNormalizado);
            return new TicketingSyncResultDto(
                    codigoNormalizado,
                    targetOpt.map(GranPremioTarget::getEventoId).orElse(null),
                    targetOpt.map(GranPremioTarget::getNombreCarrera).orElse("Desconocido"),
                    targetOpt.map(GranPremioTarget::getTemporada).orElse(null),
                    targetOpt.map(GranPremioTarget::getFechaCarrera).orElse(null),
                    0, 0, 0,
                    "WARNING",
                    "El servicio SOAP no retornó disponibilidad para " + codigoNormalizado,
                    OffsetDateTime.now(),
                    Collections.emptyList()
            );
        }

        // 2. RESOLVE: Buscar el id_evento en Supabase utilizando el año y la ciudad/circuito
        UUID idEvento = resolveEventoId(codigoNormalizado, gradasRaw);

        // 3. TRANSFORM: Mapear objetos JAX-WS a entidades EntradaGrada
        List<EntradaGrada> entradasTransformed = ticketingAdapter.toEntityList(gradasRaw, idEvento);

        // 4. LOAD (UPSERT): Guardar o actualizar en Supabase
        int entradasCreadas = 0;
        int entradasActualizadas = 0;
        List<EntradaSyncSummaryDto> summaries = new ArrayList<>();

        for (EntradaGrada transformed : entradasTransformed) {
            Optional<EntradaGrada> existingOpt = entradaGradaRepository
                    .findByIdEventoAndNombreTribunaIgnoreCase(idEvento, transformed.getNombreTribuna());

            EntradaGrada saved;
            if (existingOpt.isPresent()) {
                // Entrada existente -> Actualizar nombre exacto de la tribuna, precio y stock desde SOAP
                EntradaGrada existing = existingOpt.get();
                existing.setNombreTribuna(transformed.getNombreTribuna());
                existing.setStockDisponible(transformed.getStockDisponible());
                existing.setPrecioUsd(transformed.getPrecioUsd());
                existing.setTipo(transformed.getTipo());
                saved = entradaGradaRepository.save(existing);
                entradasActualizadas++;
                log.info("Entrada actualizada en Supabase [UPSERT]: '{}' (stock_disponible={}, id_entrada={}, evento={})",
                        saved.getNombreTribuna(), saved.getStockDisponible(), saved.getIdEntrada(), idEvento);
            } else {
                // Entrada nueva -> Insertar
                saved = entradaGradaRepository.save(transformed);
                entradasCreadas++;
                log.debug("Nueva entrada registrada [UPSERT]: '{}' (id_entrada={}, evento={})",
                        saved.getNombreTribuna(), saved.getIdEntrada(), idEvento);
            }
            summaries.add(ticketingAdapter.toSummaryDto(saved));
        }

        Optional<GranPremioTarget> targetOpt = GranPremioTarget.fromCodigoEvento(codigoNormalizado);
        String carrera = targetOpt.map(GranPremioTarget::getNombreCarrera)
                .orElse(gradasRaw.get(0).getCarrera() != null ? gradasRaw.get(0).getCarrera() : "Gran Premio");
        Integer temporada = targetOpt.map(GranPremioTarget::getTemporada)
                .orElse(extractYearFromCodigo(codigoNormalizado));
        LocalDate fechaCarrera = targetOpt.map(GranPremioTarget::getFechaCarrera).orElse(null);

        String mensaje = String.format(
                "Sincronización ETL de entradas para %s (%s) completada con éxito. Total: %d (Creadas: %d, Actualizadas: %d)",
                codigoNormalizado, carrera, summaries.size(), entradasCreadas, entradasActualizadas
        );
        log.info(mensaje);

        return new TicketingSyncResultDto(
                codigoNormalizado,
                idEvento,
                carrera,
                temporada,
                fechaCarrera,
                gradasRaw.size(),
                entradasCreadas,
                entradasActualizadas,
                "SUCCESS",
                mensaje,
                OffsetDateTime.now(),
                summaries
        );
    }

    /**
     * Resuelve el UUID del evento F1 en la tabla 'eventos_f1' de Supabase
     * utilizando el año y el nombre del circuito o ciudad derivados del codigoEvento legado.
     */
    public UUID resolveEventoId(String codigoEvento, List<InventarioGrada> gradas) {
        int year = extractYearFromCodigo(codigoEvento);

        // Identificar el nombre de la ciudad o carrera a partir del código o de la respuesta SOAP
        Optional<GranPremioTarget> targetOpt = GranPremioTarget.fromCodigoEvento(codigoEvento);
        String cityName = targetOpt.map(GranPremioTarget::getNombreCarrera)
                .orElseGet(() -> (gradas != null && !gradas.isEmpty() && gradas.get(0).getCarrera() != null)
                        ? gradas.get(0).getCarrera()
                        : extractCityHintFromCodigo(codigoEvento));

        log.info("Resolviendo id_evento en Supabase para año={}, ciudad/carrera='{}', codigo='{}'",
                year, cityName, codigoEvento);

        // 0. Si el catálogo maestro ya tiene el UUID preconfigurado (como 2026 y 2027), verificar en BD y usarlo directamente
        if (targetOpt.isPresent() && targetOpt.get().getEventoId() != null) {
            UUID predefinedId = targetOpt.get().getEventoId();
            if (eventoF1Repository.existsById(predefinedId)) {
                log.info("Evento F1 resuelto directamente por catálogo maestro: id_evento={}, codigo='{}'",
                        predefinedId, codigoEvento);
                return predefinedId;
            }
        }

        // 1. Intentar buscar en la base de datos por temporada con circuito y ciudad asociados
        List<EventoF1> eventosTemporada = eventoF1Repository.findByTemporadaWithCircuitoAndCiudad(year);
        if (!eventosTemporada.isEmpty()) {
            for (EventoF1 ev : eventosTemporada) {
                if (matchesEvent(ev, cityName, targetOpt.orElse(null))) {
                    log.info("Evento F1 encontrado en Supabase: id_evento={}, temporada={}, circuito='{}', ciudad='{}'",
                            ev.getIdEvento(), ev.getTemporada(),
                            ev.getCircuito() != null ? ev.getCircuito().getNombre() : "N/A",
                            (ev.getCircuito() != null && ev.getCircuito().getCiudad() != null) ? ev.getCircuito().getCiudad().getNombre() : "N/A");
                    return ev.getIdEvento();
                }
            }
        }

        // 2. Intentar buscar por coincidencia en todos los eventos registrados en la BD
        List<EventoF1> todosEventos = eventoF1Repository.findAllWithCircuitoAndCiudad();
        for (EventoF1 ev : todosEventos) {
            if (matchesEvent(ev, cityName, targetOpt.orElse(null))) {
                log.info("Evento F1 encontrado en búsqueda global de Supabase: id_evento={}", ev.getIdEvento());
                return ev.getIdEvento();
            }
        }

        // 3. Fallback a UUID preconfigurado en el catálogo maestro
        if (targetOpt.isPresent() && targetOpt.get().getEventoId() != null) {
            UUID fallbackId = targetOpt.get().getEventoId();
            log.warn("Evento no localizado por coincidencia directa de nombre en BD. Usando UUID predeterminado del catálogo: {}",
                    fallbackId);
            return fallbackId;
        }

        // 4. Si no se puede resolver, lanzar excepción informativa
        throw new IllegalStateException(String.format(
                "No se encontró un evento de F1 en la base de datos para la temporada %d y destino '%s' (código: %s).",
                year, cityName, codigoEvento
        ));
    }

    private boolean matchesEvent(EventoF1 ev, String cityName, GranPremioTarget target) {
        if (ev.getCircuito() == null) {
            return false;
        }

        // Coincidencia directa por UUID preconfigurado
        if (target != null && target.getEventoId() != null && target.getEventoId().equals(ev.getIdEvento())) {
            return true;
        }

        String targetNormalized = stripAccents(cityName).toLowerCase().trim();
        String circuitoNombre = ev.getCircuito().getNombre() != null
                ? stripAccents(ev.getCircuito().getNombre()).toLowerCase().trim()
                : "";
        String ciudadNombre = (ev.getCircuito().getCiudad() != null && ev.getCircuito().getCiudad().getNombre() != null)
                ? stripAccents(ev.getCircuito().getCiudad().getNombre()).toLowerCase().trim()
                : "";

        // Coincidencia con nombre de ciudad
        if (!ciudadNombre.isBlank()) {
            if (ciudadNombre.equals(targetNormalized) || ciudadNombre.contains(targetNormalized)) {
                return true;
            }
            if (targetNormalized.contains(ciudadNombre) && ciudadNombre.length() >= 4) {
                return true;
            }
        }

        // Coincidencia con nombre del circuito
        if (!circuitoNombre.isBlank()) {
            if (circuitoNombre.equals(targetNormalized) || circuitoNombre.contains(targetNormalized)) {
                return true;
            }
        }

        // Coincidencia por alias
        if (target != null) {
            for (String alias : target.getAliases()) {
                String aliasNorm = stripAccents(alias).toLowerCase().trim();
                if (aliasNorm.isBlank()) {
                    continue;
                }
                // Si el alias es corto (<= 3 caracteres), requerir igualdad exacta para evitar falsos positivos (ej: 'UK' en 'Suzuka')
                if (aliasNorm.length() <= 3) {
                    if (ciudadNombre.equalsIgnoreCase(aliasNorm) || circuitoNombre.equalsIgnoreCase(aliasNorm)) {
                        return true;
                    }
                } else {
                    if (ciudadNombre.contains(aliasNorm) || circuitoNombre.contains(aliasNorm)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private int extractYearFromCodigo(String codigo) {
        try {
            String[] parts = codigo.split("-");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {
        }
        return 2026;
    }

    private String extractCityHintFromCodigo(String codigo) {
        try {
            String[] parts = codigo.split("-");
            if (parts.length >= 3) {
                return parts[2];
            }
        } catch (Exception ignored) {
        }
        return codigo;
    }

    private String stripAccents(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
