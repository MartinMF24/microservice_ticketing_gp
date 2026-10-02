package com.uade.microservices.ticketing.config;

import jakarta.xml.ws.BindingProvider;
import java.net.URI;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import soap.ticketing.f1.legacy.F1TicketingSoapPort;
import soap.ticketing.f1.legacy.F1TicketingSoapPortService;

/**
 * Configuración del cliente JAX-WS para invocar el servicio SOAP de F1 Ticketing.
 * Crea e inyecta el proxy {@link F1TicketingSoapPort} configurado con la URL y timeouts.
 */
@Configuration
public class TicketingSoapClientConfig {

    private static final Logger log = LoggerFactory.getLogger(TicketingSoapClientConfig.class);

    private final TicketingSoapProperties properties;

    public TicketingSoapClientConfig(TicketingSoapProperties properties) {
        this.properties = properties;
    }

    @Bean
    public F1TicketingSoapPort f1TicketingSoapPort() {
        F1TicketingSoapPortService service;
        String configuredWsdl = properties.getWsdlUrl();

        try {
            log.info("Inicializando cliente JAX-WS con WSDL: {}", configuredWsdl);
            URL wsdlUrl = URI.create(configuredWsdl).toURL();
            service = new F1TicketingSoapPortService(wsdlUrl);
        } catch (Exception ex) {
            log.warn("No se pudo conectar al WSDL remoto '{}' durante el arranque ({}). Usando WSDL local empaquetado.",
                    configuredWsdl, ex.getMessage());
            URL localWsdl = getClass().getClassLoader().getResource("wsdl/ticketing.wsdl");
            if (localWsdl != null) {
                service = new F1TicketingSoapPortService(localWsdl);
            } else {
                service = new F1TicketingSoapPortService();
            }
        }

        F1TicketingSoapPort port = service.getF1TicketingSoapPortSoap11();
        BindingProvider bp = (BindingProvider) port;

        // Configurar dirección del endpoint de destino
        String endpoint = properties.getEndpointUrl();
        if (endpoint != null && !endpoint.isBlank()) {
            bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
            log.info("Endpoint SOAP configurado en: {}", endpoint);
        }

        // Timeouts para HTTP connection y receive
        bp.getRequestContext().put("jakarta.xml.ws.client.connectionTimeout", properties.getConnectTimeout());
        bp.getRequestContext().put("jakarta.xml.ws.client.receiveTimeout", properties.getReadTimeout());

        return port;
    }
}
