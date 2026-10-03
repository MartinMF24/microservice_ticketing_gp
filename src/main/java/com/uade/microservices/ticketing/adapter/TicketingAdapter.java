package com.uade.microservices.ticketing.adapter;

import com.uade.microservices.ticketing.dto.response.EntradaSyncSummaryDto;
import com.uade.microservices.ticketing.model.EntradaGrada;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import soap.ticketing.f1.legacy.InventarioGrada;

/**
 * Fase TRANSFORM del módulo ETL.
 * Implementa el Patrón Adapter para transformar los objetos crudos devueltos por el
 * servicio SOAP legado (JAX-WS {@link InventarioGrada}) a la entidad del dominio {@link EntradaGrada}.
 *
 * Aplica la regla de negocio crítica de la restricción CHECK de PostgreSQL (entradas_gradas_tipo_check),
 * normalizando las categorías legadas ("VIP", "Premium", "Standard", "General") al conjunto
 * permitido: ('VIP', 'Asiento Numerado', 'General').
 */
@Component
public class TicketingAdapter {

    /**
     * Mapea una lista de objetos InventarioGrada del SOAP a una lista de entidades EntradaGrada.
     *
     * @param gradasLegacy Lista de gradas obtenida de la llamada SOAP.
     * @param idEvento     UUID del evento F1 en Supabase.
     * @return Lista de entidades EntradaGrada transformadas.
     */
    public List<EntradaGrada> toEntityList(List<InventarioGrada> gradasLegacy, UUID idEvento) {
        if (gradasLegacy == null || gradasLegacy.isEmpty()) {
            return Collections.emptyList();
        }
        return gradasLegacy.stream()
                .map(grada -> toEntity(grada, idEvento))
                .toList();
    }

    /**
     * Mapea un objeto InventarioGrada individual a una entidad EntradaGrada.
     *
     * @param grada    Objeto grada devuelto por SOAP JAX-WS.
     * @param idEvento UUID del evento F1 en Supabase.
     * @return Entidad EntradaGrada lista para persistir.
     */
    public EntradaGrada toEntity(InventarioGrada grada, UUID idEvento) {
        if (grada == null) {
            return null;
        }

        EntradaGrada entrada = new EntradaGrada();
        entrada.setIdEvento(idEvento);

        // 1. Nombre de la tribuna: mantener exactamente el mismo nombre que tiene en el SOAP
        String tribuna = grada.getTribuna() != null ? grada.getTribuna().trim() : "";
        if (tribuna.length() > 100) {
            tribuna = tribuna.substring(0, 100).trim();
        }
        entrada.setNombreTribuna(tribuna);

        // 2. Precio en USD (numeric 10, 2)
        BigDecimal precio = grada.getPrecioUsd() != null ? grada.getPrecioUsd() : BigDecimal.ZERO;
        entrada.setPrecioUsd(precio.setScale(2, RoundingMode.HALF_UP));

        // 3. Stock disponible: cargar en la columna stock_disponible exactamente el mismo número que en el stock del SOAP
        entrada.setStockDisponible(grada.getStock());

        // 4. Regla de Negocio Crítica: Mapeo de categoría hacia el CHECK entradas_gradas_tipo_check
        entrada.setTipo(mapTipo(grada.getCategoria()));

        // 5. Auditoría
        entrada.setCreatedAt(OffsetDateTime.now());

        return entrada;
    }

    /**
     * Regla de Negocio Crítica (Restricción CHECK entradas_gradas_tipo_check):
     * La base de datos solo admite los valores: 'VIP', 'Asiento Numerado' y 'General'.
     * El sistema legado SOAP devuelve 'VIP', 'Premium', 'Standard' y 'General'.
     *
     * Mapeo:
     * - "VIP"      -> "VIP"
     * - "Premium"  -> "Asiento Numerado"
     * - "Standard" -> "Asiento Numerado"
     * - "General"  -> "General"
     *
     * @param categoriaLegacy Categoría devuelta por el sistema legado SOAP.
     * @return Tipo normalizado acorde al CHECK constraint.
     */
    public String mapTipo(String categoriaLegacy) {
        if (categoriaLegacy == null || categoriaLegacy.isBlank()) {
            return EntradaGrada.TIPO_GENERAL;
        }

        String raw = categoriaLegacy.trim().toUpperCase();

        return switch (raw) {
            case "VIP" -> EntradaGrada.TIPO_VIP;
            case "PREMIUM", "STANDARD", "ASIENTO NUMERADO", "ASIENTO_NUMERADO" -> EntradaGrada.TIPO_ASIENTO_NUMERADO;
            case "GENERAL" -> EntradaGrada.TIPO_GENERAL;
            default -> {
                if (raw.contains("VIP")) {
                    yield EntradaGrada.TIPO_VIP;
                }
                if (raw.contains("PREMIUM") || raw.contains("STAND") || raw.contains("ASIENTO") || raw.contains("TRIBUNA")) {
                    yield EntradaGrada.TIPO_ASIENTO_NUMERADO;
                }
                yield EntradaGrada.TIPO_GENERAL;
            }
        };
    }

    /**
     * Convierte una entidad EntradaGrada a un DTO resumen de salida.
     */
    public EntradaSyncSummaryDto toSummaryDto(EntradaGrada entrada) {
        return new EntradaSyncSummaryDto(
                entrada.getIdEntrada(),
                entrada.getIdEvento(),
                entrada.getNombreTribuna(),
                entrada.getPrecioUsd(),
                entrada.getStockDisponible(),
                entrada.getTipo()
        );
    }
}
