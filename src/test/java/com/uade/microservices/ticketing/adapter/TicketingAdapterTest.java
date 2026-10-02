package com.uade.microservices.ticketing.adapter;

import com.uade.microservices.ticketing.model.EntradaGrada;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soap.ticketing.f1.legacy.InventarioGrada;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketingAdapterTest {

    private TicketingAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TicketingAdapter();
    }

    @Test
    @DisplayName("Regla de Negocio Crítica: Debe mapear 'VIP', 'Premium', 'Standard' y 'General' a los valores admitidos por el CHECK")
    void shouldMapCategoriesAccordingToDatabaseCheckConstraint() {
        // La restricción CHECK entradas_gradas_tipo_check solo acepta 'VIP', 'Asiento Numerado' y 'General'
        assertEquals(EntradaGrada.TIPO_VIP, adapter.mapTipo("VIP"));
        assertEquals(EntradaGrada.TIPO_VIP, adapter.mapTipo("vip"));

        // 'Premium' y 'Standard' deben mapearse obligatoriamente a 'Asiento Numerado'
        assertEquals(EntradaGrada.TIPO_ASIENTO_NUMERADO, adapter.mapTipo("Premium"));
        assertEquals(EntradaGrada.TIPO_ASIENTO_NUMERADO, adapter.mapTipo("premium"));
        assertEquals(EntradaGrada.TIPO_ASIENTO_NUMERADO, adapter.mapTipo("Standard"));
        assertEquals(EntradaGrada.TIPO_ASIENTO_NUMERADO, adapter.mapTipo("standard"));

        // 'General' mapea a 'General'
        assertEquals(EntradaGrada.TIPO_GENERAL, adapter.mapTipo("General"));
        assertEquals(EntradaGrada.TIPO_GENERAL, adapter.mapTipo("general"));

        // Fallbacks seguros a valores permitidos
        assertEquals(EntradaGrada.TIPO_GENERAL, adapter.mapTipo(null));
        assertEquals(EntradaGrada.TIPO_GENERAL, adapter.mapTipo(""));
        assertEquals(EntradaGrada.TIPO_ASIENTO_NUMERADO, adapter.mapTipo("Tribuna Numerada"));
    }

    @Test
    @DisplayName("Debe transformar un InventarioGrada SOAP a entidad EntradaGrada con UUID de evento")
    void shouldTransformInventarioGradaToEntradaGrada() {
        UUID idEvento = UUID.randomUUID();

        InventarioGrada grada = new InventarioGrada();
        grada.setId(101L);
        grada.setCodigoEvento("F1-2026-MAD");
        grada.setCarrera("Madrid");
        grada.setTribuna("Tribuna Principal");
        grada.setCategoria("Premium");
        grada.setPrecioUsd(new BigDecimal("1200.00"));
        grada.setStock(2500);

        EntradaGrada entrada = adapter.toEntity(grada, idEvento);

        assertNotNull(entrada);
        assertEquals(idEvento, entrada.getIdEvento());
        assertEquals("Tribuna Principal", entrada.getNombreTribuna());
        assertEquals(EntradaGrada.TIPO_ASIENTO_NUMERADO, entrada.getTipo());
        assertEquals(new BigDecimal("1200.00"), entrada.getPrecioUsd());
        assertEquals(2500, entrada.getStockDisponible());
        assertNotNull(entrada.getCreatedAt());
    }

    @Test
    @DisplayName("Debe truncar nombres de tribuna excesivamente largos a 100 caracteres")
    void shouldTruncateExcessivelyLongTribunaNames() {
        UUID idEvento = UUID.randomUUID();
        String nombreLargo = "Tribuna Especial Super VIP Platinum Exclusiva ".repeat(5);

        InventarioGrada grada = new InventarioGrada();
        grada.setTribuna(nombreLargo);
        grada.setCategoria("VIP");
        grada.setPrecioUsd(new BigDecimal("4500.00"));
        grada.setStock(100);

        EntradaGrada entrada = adapter.toEntity(grada, idEvento);

        assertTrue(entrada.getNombreTribuna().length() <= 100);
    }

    @Test
    @DisplayName("Debe transformar lista completa de gradas SOAP")
    void shouldTransformList() {
        UUID idEvento = UUID.randomUUID();

        InventarioGrada g1 = new InventarioGrada();
        g1.setTribuna("Paddock Club");
        g1.setCategoria("VIP");
        g1.setPrecioUsd(new BigDecimal("3500.00"));
        g1.setStock(500);

        InventarioGrada g2 = new InventarioGrada();
        g2.setTribuna("Pelouse");
        g2.setCategoria("General");
        g2.setPrecioUsd(new BigDecimal("200.00"));
        g2.setStock(10000);

        List<EntradaGrada> entradas = adapter.toEntityList(List.of(g1, g2), idEvento);

        assertEquals(2, entradas.size());
        assertEquals(EntradaGrada.TIPO_VIP, entradas.get(0).getTipo());
        assertEquals(EntradaGrada.TIPO_GENERAL, entradas.get(1).getTipo());
    }
}
