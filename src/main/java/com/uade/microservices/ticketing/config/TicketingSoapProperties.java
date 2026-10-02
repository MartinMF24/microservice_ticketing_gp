package com.uade.microservices.ticketing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de configuración para la conexión con el servicio legado SOAP F1 Ticketing.
 */
@Component
@ConfigurationProperties(prefix = "ticketing.soap")
public class TicketingSoapProperties {

    /**
     * URL del WSDL publicado por el servicio SOAP (variable de entorno TICKETING_WSDL_URL).
     */
    private String wsdlUrl = "http://localhost:8080/ws/ticketing.wsdl";

    /**
     * URL del endpoint SOAP directo donde se despachan las solicitudes (POST).
     */
    private String endpointUrl = "http://localhost:8080/ws/ticketing";

    /**
     * Timeout de conexión en milisegundos.
     */
    private int connectTimeout = 10000;

    /**
     * Timeout de lectura en milisegundos.
     */
    private int readTimeout = 20000;

    /**
     * Habilita el mecanismo de fallback con el catálogo interno en caso de indisponibilidad del servicio SOAP.
     */
    private boolean fallbackEnabled = true;

    public String getWsdlUrl() {
        return wsdlUrl;
    }

    public void setWsdlUrl(String wsdlUrl) {
        this.wsdlUrl = wsdlUrl;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}
