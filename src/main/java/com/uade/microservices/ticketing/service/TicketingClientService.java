package com.uade.microservices.ticketing.service;

import com.uade.microservices.ticketing.config.TicketingSoapProperties;
import com.uade.microservices.ticketing.model.GranPremioTarget;
import jakarta.xml.ws.WebServiceException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import soap.ticketing.f1.legacy.ConsultarDisponibilidadRequest;
import soap.ticketing.f1.legacy.ConsultarDisponibilidadResponse;
import soap.ticketing.f1.legacy.F1TicketingSoapPort;
import soap.ticketing.f1.legacy.InventarioGrada;

/**
 * Fase EXTRACT del módulo ETL.
 * Se encarga de consumir la operación SOAP consultarDisponibilidad(codigoEvento)
 * del servicio legado F1 Ticketing a través del cliente JAX-WS stub.
 */
@Service
public class TicketingClientService {

    private static final Logger log = LoggerFactory.getLogger(TicketingClientService.class);

    private final F1TicketingSoapPort soapPort;
    private final TicketingSoapProperties properties;

    public TicketingClientService(F1TicketingSoapPort soapPort, TicketingSoapProperties properties) {
        this.soapPort = soapPort;
        this.properties = properties;
    }

    /**
     * Llama a la operación SOAP consultarDisponibilidad pasando el código del evento (ej: "F1-2026-MAD").
     *
     * @param codigoEvento Código del evento en formato legado (ej: "F1-2026-MAD").
     * @return Lista de objetos InventarioGrada devueltos por el servicio SOAP.
     */
    public List<InventarioGrada> extractEntradas(String codigoEvento) {
        if (codigoEvento == null || codigoEvento.isBlank()) {
            throw new IllegalArgumentException("El código del evento no puede ser nulo ni vacío.");
        }

        String codigoNormalizado = codigoEvento.trim().toUpperCase();
        log.info("Ejecutando llamada SOAP consultarDisponibilidad para codigoEvento: {}", codigoNormalizado);

        try {
            ConsultarDisponibilidadRequest request = new ConsultarDisponibilidadRequest();
            request.setCodigoEvento(codigoNormalizado);

            ConsultarDisponibilidadResponse response = soapPort.consultarDisponibilidad(request);

            if (response != null && response.getGradas() != null && !response.getGradas().isEmpty()) {
                log.info("Llamada SOAP exitosa para {}: {} gradas obtenidas", codigoNormalizado, response.getGradas().size());
                return response.getGradas();
            }

            log.warn("El servicio SOAP respondió pero no retornó gradas para el evento {}", codigoNormalizado);
            return Collections.emptyList();

        } catch (Exception ex) {
            log.error("Fallo al consumir la operación SOAP consultarDisponibilidad para {}: {}",
                    codigoNormalizado, ex.getMessage());

            if (properties.isFallbackEnabled()) {
                log.warn("Fallback de contingencia activado: generando inventario representativo para {}", codigoNormalizado);
                return generateFallbackGradas(codigoNormalizado);
            }

            throw new WebServiceException("No se pudo obtener disponibilidad del servicio SOAP para '"
                    + codigoNormalizado + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * Genera datos de contingencia alineados con el catálogo de f1-ticketing-legacy-soap
     * para asegurar continuidad operativa y testeabilidad cuando el servicio SOAP remoto esté temporalmente inaccesible.
     */
    private List<InventarioGrada> generateFallbackGradas(String codigoEvento) {
        Optional<GranPremioTarget> targetOpt = GranPremioTarget.fromCodigoEvento(codigoEvento);
        String carrera = targetOpt.map(GranPremioTarget::getNombreCarrera).orElse("Gran Premio");

        List<InventarioGrada> gradas = new ArrayList<>();

        // 1. VIP
        InventarioGrada vip = new InventarioGrada();
        vip.setCodigoEvento(codigoEvento);
        vip.setCarrera(carrera);
        vip.setTribuna("Paddock Club " + carrera);
        vip.setCategoria("VIP");
        vip.setPrecioUsd(new BigDecimal("3500.00"));
        vip.setStock(500);
        gradas.add(vip);

        // 2. Premium (que mapea a 'Asiento Numerado')
        InventarioGrada premium = new InventarioGrada();
        premium.setCodigoEvento(codigoEvento);
        premium.setCarrera(carrera);
        premium.setTribuna("Tribuna Principal " + carrera);
        premium.setCategoria("Premium");
        premium.setPrecioUsd(new BigDecimal("1200.00"));
        premium.setStock(2500);
        gradas.add(premium);

        // 3. Standard (que mapea a 'Asiento Numerado')
        InventarioGrada standard = new InventarioGrada();
        standard.setCodigoEvento(codigoEvento);
        standard.setCarrera(carrera);
        standard.setTribuna("Grada Curva " + carrera);
        standard.setCategoria("Standard");
        standard.setPrecioUsd(new BigDecimal("550.00"));
        standard.setStock(5000);
        gradas.add(standard);

        // 4. General (que mapea a 'General')
        InventarioGrada general = new InventarioGrada();
        general.setCodigoEvento(codigoEvento);
        general.setCarrera(carrera);
        general.setTribuna("Pelouse / Admisión General " + carrera);
        general.setCategoria("General");
        general.setPrecioUsd(new BigDecimal("200.00"));
        general.setStock(12000);
        gradas.add(general);

        return gradas;
    }
}
