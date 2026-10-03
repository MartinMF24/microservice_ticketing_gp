package com.uade.microservices.ticketing.service;

import com.uade.microservices.ticketing.config.TicketingSoapProperties;
import com.uade.microservices.ticketing.model.GranPremioTarget;
import com.uade.microservices.ticketing.shared.exception.StockInsuficienteException;
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
import soap.ticketing.f1.legacy.ReservarEntradasRequest;
import soap.ticketing.f1.legacy.ReservarEntradasResponse;
import soap.ticketing.f1.legacy.SinStockFault_Exception;

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
     * Genera datos de contingencia estrictamente alineados con el catálogo de f1-ticketing-legacy-soap (data.sql)
     * asegurando que los nombres de las tribunas y los números de stock coincidan 100% con el sistema SOAP.
     */
    private List<InventarioGrada> generateFallbackGradas(String codigoEvento) {
        String codigoNormalizado = codigoEvento.trim().toUpperCase();

        return switch (codigoNormalizado) {
            case "F1-2026-MAD" -> List.of(
                    crearGrada(codigoNormalizado, "Madrid", "Paddock Club Madrid", "VIP", "3500.00", 500),
                    crearGrada(codigoNormalizado, "Madrid", "Tribuna Principal", "Premium", "1200.00", 2500),
                    crearGrada(codigoNormalizado, "Madrid", "Grada T4", "Standard", "550.00", 5000),
                    crearGrada(codigoNormalizado, "Madrid", "Pelouse", "General", "200.00", 12000)
            );
            case "F1-2026-BAK" -> List.of(
                    crearGrada(codigoNormalizado, "Bakú", "Paddock Club Azerbaijan", "VIP", "3200.00", 400),
                    crearGrada(codigoNormalizado, "Bakú", "Absheron Grandstand", "Premium", "950.00", 2000),
                    crearGrada(codigoNormalizado, "Bakú", "Azneft Grandstand", "Standard", "450.00", 4500),
                    crearGrada(codigoNormalizado, "Bakú", "General Admission", "General", "150.00", 8000)
            );
            case "F1-2026-SIN" -> List.of(
                    crearGrada(codigoNormalizado, "Singapur", "Singapore Paddock Club", "VIP", "4500.00", 600),
                    crearGrada(codigoNormalizado, "Singapur", "Pit Grandstand", "Premium", "1500.00", 3000),
                    crearGrada(codigoNormalizado, "Singapur", "Padang Grandstand", "Standard", "650.00", 6000),
                    crearGrada(codigoNormalizado, "Singapur", "Premier Walkabout", "General", "300.00", 10000)
            );
            case "F1-2026-AUS" -> List.of(
                    crearGrada(codigoNormalizado, "Austin", "Paddock Club COTA", "VIP", "3800.00", 550),
                    crearGrada(codigoNormalizado, "Austin", "Main Grandstand", "Premium", "1300.00", 3500),
                    crearGrada(codigoNormalizado, "Austin", "Turn 12 Grandstand", "Standard", "600.00", 7000),
                    crearGrada(codigoNormalizado, "Austin", "General Admission", "General", "250.00", 15000)
            );
            case "F1-2026-MEX" -> List.of(
                    crearGrada(codigoNormalizado, "Ciudad de México", "Paddock Club Mexico", "VIP", "4000.00", 500),
                    crearGrada(codigoNormalizado, "Ciudad de México", "Grada 1 (Principal)", "Premium", "1400.00", 4000),
                    crearGrada(codigoNormalizado, "Ciudad de México", "Foro Sol Norte", "Standard", "750.00", 8000),
                    crearGrada(codigoNormalizado, "Ciudad de México", "Admisión General", "General", "250.00", 12000)
            );
            case "F1-2026-SAO" -> List.of(
                    crearGrada(codigoNormalizado, "São Paulo", "VIP Club Interlagos", "VIP", "3200.00", 400),
                    crearGrada(codigoNormalizado, "São Paulo", "Grandstand B (Recta)", "Premium", "1100.00", 3000),
                    crearGrada(codigoNormalizado, "São Paulo", "Grandstand M (Senna S)", "Standard", "550.00", 5000),
                    crearGrada(codigoNormalizado, "São Paulo", "Sector G (General)", "General", "200.00", 15000)
            );
            case "F1-2026-LAS" -> List.of(
                    crearGrada(codigoNormalizado, "Las Vegas", "Bellagio Fountain Club", "VIP", "5500.00", 800),
                    crearGrada(codigoNormalizado, "Las Vegas", "East Harmon Zone", "Premium", "2000.00", 4000),
                    crearGrada(codigoNormalizado, "Las Vegas", "MSG Sphere Zone", "Standard", "900.00", 6000),
                    crearGrada(codigoNormalizado, "Las Vegas", "General Admission", "General", "400.00", 10000)
            );
            case "F1-2026-QAT" -> List.of(
                    crearGrada(codigoNormalizado, "Lusail", "Paddock Club Qatar", "VIP", "3500.00", 500),
                    crearGrada(codigoNormalizado, "Lusail", "Main Grandstand", "Premium", "1000.00", 2500),
                    crearGrada(codigoNormalizado, "Lusail", "North Grandstand", "Standard", "450.00", 4500),
                    crearGrada(codigoNormalizado, "Lusail", "General Admission", "General", "180.00", 8000)
            );
            case "F1-2026-ABU" -> List.of(
                    crearGrada(codigoNormalizado, "Abu Dabi", "Paddock Club Yas Marina", "VIP", "4200.00", 600),
                    crearGrada(codigoNormalizado, "Abu Dabi", "Main Grandstand", "Premium", "1350.00", 3000),
                    crearGrada(codigoNormalizado, "Abu Dabi", "West Grandstand", "Standard", "650.00", 5500),
                    crearGrada(codigoNormalizado, "Abu Dabi", "Abu Dhabi Hill", "General", "250.00", 10000)
            );
            case "F1-2027-BAH" -> List.of(
                    crearGrada(codigoNormalizado, "Bahréin", "Paddock Club Bahrain", "VIP", "3400.00", 450),
                    crearGrada(codigoNormalizado, "Bahréin", "Main Grandstand", "Premium", "950.00", 2500),
                    crearGrada(codigoNormalizado, "Bahréin", "Turn 1 Grandstand", "Standard", "450.00", 4000),
                    crearGrada(codigoNormalizado, "Bahréin", "General Admission", "General", "180.00", 8000)
            );
            case "F1-2027-SAU" -> List.of(
                    crearGrada(codigoNormalizado, "Arabia Saudita", "Paddock Club Jeddah", "VIP", "4000.00", 500),
                    crearGrada(codigoNormalizado, "Arabia Saudita", "Main Grandstand", "Premium", "1200.00", 2800),
                    crearGrada(codigoNormalizado, "Arabia Saudita", "Central Grandstand", "Standard", "550.00", 5000),
                    crearGrada(codigoNormalizado, "Arabia Saudita", "General Admission", "General", "220.00", 9000)
            );
            case "F1-2027-MEL" -> List.of(
                    crearGrada(codigoNormalizado, "Australia", "Paddock Club Melbourne", "VIP", "3800.00", 600),
                    crearGrada(codigoNormalizado, "Australia", "Brabham Grandstand", "Premium", "1150.00", 3500),
                    crearGrada(codigoNormalizado, "Australia", "Jones Grandstand", "Standard", "550.00", 6000),
                    crearGrada(codigoNormalizado, "Australia", "Park Pass (GA)", "General", "200.00", 20000)
            );
            case "F1-2027-SUZ" -> List.of(
                    crearGrada(codigoNormalizado, "Japón", "Paddock Club Suzuka", "VIP", "3600.00", 500),
                    crearGrada(codigoNormalizado, "Japón", "Grandstand V1", "Premium", "1050.00", 3000),
                    crearGrada(codigoNormalizado, "Japón", "Grandstand B2", "Standard", "500.00", 5500),
                    crearGrada(codigoNormalizado, "Japón", "West Area Ticket", "General", "180.00", 12000)
            );
            case "F1-2027-SHA" -> List.of(
                    crearGrada(codigoNormalizado, "China", "Paddock Club Shanghai", "VIP", "3200.00", 400),
                    crearGrada(codigoNormalizado, "China", "Grandstand A (Main)", "Premium", "900.00", 2500),
                    crearGrada(codigoNormalizado, "China", "Grandstand H", "Standard", "400.00", 5000),
                    crearGrada(codigoNormalizado, "China", "General Admission", "General", "150.00", 10000)
            );
            case "F1-2027-MIA" -> List.of(
                    crearGrada(codigoNormalizado, "Miami", "Paddock Club Miami", "VIP", "4800.00", 650),
                    crearGrada(codigoNormalizado, "Miami", "Start/Finish Grandstand", "Premium", "1600.00", 3500),
                    crearGrada(codigoNormalizado, "Miami", "Turn 18 Grandstand", "Standard", "800.00", 6500),
                    crearGrada(codigoNormalizado, "Miami", "Marina Grandstand", "Standard", "700.00", 4000)
            );
            case "F1-2027-MON" -> List.of(
                    crearGrada(codigoNormalizado, "Canadá", "Paddock Club Montreal", "VIP", "3600.00", 550),
                    crearGrada(codigoNormalizado, "Canadá", "Grandstand 11", "Premium", "1000.00", 3000),
                    crearGrada(codigoNormalizado, "Canadá", "Grandstand 31", "Standard", "450.00", 5000),
                    crearGrada(codigoNormalizado, "Canadá", "General Admission", "General", "180.00", 15000)
            );
            case "F1-2027-MCO" -> List.of(
                    crearGrada(codigoNormalizado, "Mónaco", "Monaco VIP Club", "VIP", "5000.00", 300),
                    crearGrada(codigoNormalizado, "Mónaco", "Tribune K (Tabac)", "Premium", "1800.00", 1500),
                    crearGrada(codigoNormalizado, "Mónaco", "Tribune O (Piscine)", "Standard", "950.00", 2500),
                    crearGrada(codigoNormalizado, "Mónaco", "Rocher (General)", "General", "350.00", 5000)
            );
            case "F1-2027-POR" -> List.of(
                    crearGrada(codigoNormalizado, "Portugal", "Paddock Club Portimão", "VIP", "3000.00", 400),
                    crearGrada(codigoNormalizado, "Portugal", "Bancada Principal", "Premium", "800.00", 2000),
                    crearGrada(codigoNormalizado, "Portugal", "Bancada Portimão", "Standard", "350.00", 4500),
                    crearGrada(codigoNormalizado, "Portugal", "Peão (General)", "General", "120.00", 10000)
            );
            case "F1-2027-SIL" -> List.of(
                    crearGrada(codigoNormalizado, "Gran Bretaña", "Paddock Club Silverstone", "VIP", "4200.00", 600),
                    crearGrada(codigoNormalizado, "Gran Bretaña", "Hamilton Straight", "Premium", "1400.00", 3500),
                    crearGrada(codigoNormalizado, "Gran Bretaña", "Becketts", "Standard", "650.00", 6500),
                    crearGrada(codigoNormalizado, "Gran Bretaña", "General Admission", "General", "280.00", 20000)
            );
            default -> {
                Optional<GranPremioTarget> targetOpt = GranPremioTarget.fromCodigoEvento(codigoNormalizado);
                String carrera = targetOpt.map(GranPremioTarget::getNombreCarrera).orElse("Gran Premio");
                yield List.of(
                        crearGrada(codigoNormalizado, carrera, "Paddock Club " + carrera, "VIP", "3500.00", 500),
                        crearGrada(codigoNormalizado, carrera, "Tribuna Principal " + carrera, "Premium", "1200.00", 2500),
                        crearGrada(codigoNormalizado, carrera, "Grada Curva " + carrera, "Standard", "550.00", 5000),
                        crearGrada(codigoNormalizado, carrera, "Admisión General " + carrera, "General", "200.00", 12000)
                );
            }
        };
    }

    private InventarioGrada crearGrada(String codigoEvento, String carrera, String tribuna,
                                       String categoria, String precioUsd, int stock) {
        InventarioGrada grada = new InventarioGrada();
        grada.setCodigoEvento(codigoEvento);
        grada.setCarrera(carrera);
        grada.setTribuna(tribuna);
        grada.setCategoria(categoria);
        grada.setPrecioUsd(new BigDecimal(precioUsd));
        grada.setStock(stock);
        return grada;
    }

    /**
     * Consume la operación SOAP reservarEntradas(codigoEvento, tribuna, cantidad) del sistema legado F1.
     * Esta operación modifica el stock directamente en el sistema legado SOAP y genera un código de confirmación.
     *
     * @param codigoEvento Código del evento legado (ej: "F1-2026-MAD").
     * @param tribuna      Nombre exacto de la tribuna (ej: "Paddock Club Madrid").
     * @param cantidad     Cantidad de entradas solicitadas (> 0).
     * @return Código alfanumérico de confirmación emitido por el servicio SOAP (ej: "TKT-99812").
     * @throws StockInsuficienteException si el servicio SOAP rechaza la reserva por falta de stock.
     * @throws WebServiceException        si ocurre un fallo de comunicación o red con el servicio SOAP.
     */
    public String reservarEntradas(String codigoEvento, String tribuna, int cantidad) {
        if (codigoEvento == null || codigoEvento.isBlank()) {
            throw new IllegalArgumentException("El código del evento no puede ser nulo ni vacío.");
        }
        if (tribuna == null || tribuna.isBlank()) {
            throw new IllegalArgumentException("El nombre de la tribuna no puede ser nulo ni vacío.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad solicitada debe ser mayor a 0.");
        }

        String codigoNormalizado = codigoEvento.trim().toUpperCase();
        String tribunaNormalizada = tribuna.trim();
        log.info("Invocando operación SOAP reservarEntradas: evento={}, tribuna='{}', cantidad={}",
                codigoNormalizado, tribunaNormalizada, cantidad);

        try {
            ReservarEntradasRequest request = new ReservarEntradasRequest();
            request.setCodigoEvento(codigoNormalizado);
            request.setTribuna(tribunaNormalizada);
            request.setCantidad(cantidad);

            ReservarEntradasResponse response = soapPort.reservarEntradas(request);

            if (response != null && response.getCodigoConfirmacion() != null && !response.getCodigoConfirmacion().isBlank()) {
                String codigoConfirmacion = response.getCodigoConfirmacion().trim();
                log.info("Reserva exitosa en SOAP para {} - '{}': códigoConfirmación={}",
                        codigoNormalizado, tribunaNormalizada, codigoConfirmacion);
                return codigoConfirmacion;
            }

            log.warn("El servicio SOAP respondió pero no retornó un código de confirmación para {} - '{}'",
                    codigoNormalizado, tribunaNormalizada);
            throw new WebServiceException("El servicio SOAP no retornó un código de confirmación para la reserva.");

        } catch (SinStockFault_Exception ex) {
            String errorMsg = (ex.getFaultInfo() != null && ex.getFaultInfo().getMensaje() != null)
                    ? ex.getFaultInfo().getMensaje()
                    : ex.getMessage();
            String errorCode = (ex.getFaultInfo() != null && ex.getFaultInfo().getCodigoError() != null)
                    ? ex.getFaultInfo().getCodigoError()
                    : "SIN_STOCK";

            log.warn("Rechazo de reserva en SOAP [{}]: {}", errorCode, errorMsg);
            throw new StockInsuficienteException(errorMsg);

        } catch (Exception ex) {
            if (ex instanceof StockInsuficienteException) {
                throw (StockInsuficienteException) ex;
            }
            log.error("Fallo al consumir la operación SOAP reservarEntradas para {} - '{}': {}",
                    codigoNormalizado, tribunaNormalizada, ex.getMessage(), ex);

            if (properties.isFallbackEnabled()) {
                String fallbackCode = "TKT-FBK-" + java.util.concurrent.ThreadLocalRandom.current().nextInt(10000, 99999);
                log.warn("Fallback de contingencia activado: generando código de confirmación simulado '{}'", fallbackCode);
                return fallbackCode;
            }

            throw new WebServiceException("No se pudo ejecutar la reserva en el servicio SOAP para '"
                    + codigoNormalizado + " / " + tribunaNormalizada + "': " + ex.getMessage(), ex);
        }
    }
}
