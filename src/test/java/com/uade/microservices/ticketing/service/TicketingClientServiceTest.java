package com.uade.microservices.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.uade.microservices.ticketing.config.TicketingSoapProperties;
import com.uade.microservices.ticketing.shared.exception.StockInsuficienteException;
import jakarta.xml.ws.WebServiceException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soap.ticketing.f1.legacy.ConsultarDisponibilidadRequest;
import soap.ticketing.f1.legacy.ConsultarDisponibilidadResponse;
import soap.ticketing.f1.legacy.F1TicketingSoapPort;
import soap.ticketing.f1.legacy.InventarioGrada;
import soap.ticketing.f1.legacy.ReservarEntradasRequest;
import soap.ticketing.f1.legacy.ReservarEntradasResponse;
import soap.ticketing.f1.legacy.SinStockFault;
import soap.ticketing.f1.legacy.SinStockFault_Exception;

@ExtendWith(MockitoExtension.class)
class TicketingClientServiceTest {

    @Mock
    private F1TicketingSoapPort soapPort;

    private TicketingSoapProperties properties;
    private TicketingClientService clientService;

    @BeforeEach
    void setUp() {
        properties = new TicketingSoapProperties();
        properties.setFallbackEnabled(true);
        clientService = new TicketingClientService(soapPort, properties);
    }

    @Test
    @DisplayName("reservarEntradas - Debe retornar el código de confirmación cuando SOAP responde exitosamente")
    void reservarEntradas_Exitoso() throws Exception {
        ReservarEntradasResponse response = new ReservarEntradasResponse();
        response.setCodigoConfirmacion("TKT-99887");

        when(soapPort.reservarEntradas(any(ReservarEntradasRequest.class))).thenReturn(response);

        String confirmation = clientService.reservarEntradas("F1-2026-MAD", "Paddock Club Madrid", 2);

        assertThat(confirmation).isEqualTo("TKT-99887");
    }

    @Test
    @DisplayName("reservarEntradas - Debe mapear SinStockFault_Exception a StockInsuficienteException")
    void reservarEntradas_SinStockFault() throws Exception {
        SinStockFault fault = new SinStockFault();
        fault.setMensaje("No hay suficiente stock para la tribuna Pelouse");
        fault.setCodigoError("ERR-STOCK-INSUFICIENTE");

        SinStockFault_Exception soapFault = new SinStockFault_Exception("Fallo SOAP", fault);
        when(soapPort.reservarEntradas(any(ReservarEntradasRequest.class))).thenThrow(soapFault);

        assertThatThrownBy(() -> clientService.reservarEntradas("F1-2026-MAD", "Pelouse", 10))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("No hay suficiente stock para la tribuna Pelouse");
    }

    @Test
    @DisplayName("reservarEntradas - Debe generar código fallback si ocurre error de conexión y fallback está activo")
    void reservarEntradas_Fallback() throws Exception {
        when(soapPort.reservarEntradas(any(ReservarEntradasRequest.class)))
                .thenThrow(new RuntimeException("Connection timed out"));

        String confirmation = clientService.reservarEntradas("F1-2026-MAD", "Pelouse", 2);

        assertThat(confirmation).isNotNull();
        assertThat(confirmation).startsWith("TKT-FBK-");
    }

    @Test
    @DisplayName("reservarEntradas - Debe validar que los parámetros no sean nulos o inválidos")
    void reservarEntradas_ValidacionParametros() {
        assertThatThrownBy(() -> clientService.reservarEntradas(null, "Tribuna", 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> clientService.reservarEntradas("F1-2026-MAD", "", 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> clientService.reservarEntradas("F1-2026-MAD", "Tribuna", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("extractEntradas - Debe retornar gradas cuando el servicio SOAP responde")
    void extractEntradas_Exitoso() {
        ConsultarDisponibilidadResponse response = new ConsultarDisponibilidadResponse();
        InventarioGrada grada = new InventarioGrada();
        grada.setTribuna("Paddock Club Madrid");
        response.getGradas().add(grada);

        when(soapPort.consultarDisponibilidad(any(ConsultarDisponibilidadRequest.class))).thenReturn(response);

        List<InventarioGrada> result = clientService.extractEntradas("F1-2026-MAD");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTribuna()).isEqualTo("Paddock Club Madrid");
    }
}
