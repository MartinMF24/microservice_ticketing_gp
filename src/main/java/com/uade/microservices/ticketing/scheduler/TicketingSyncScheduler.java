package com.uade.microservices.ticketing.scheduler;

import com.uade.microservices.ticketing.dto.response.TicketingSyncAllSummaryDto;
import com.uade.microservices.ticketing.service.TicketingSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Programador de tareas periódicas (CRON) para la sincronización automática de entradas y stock.
 * Se ejecuta automáticamente una vez al día según la expresión cron configurada.
 */
@Component
@ConditionalOnProperty(name = "ticketing.sync.cron.enabled", havingValue = "true", matchIfMissing = true)
public class TicketingSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(TicketingSyncScheduler.class);

    private final TicketingSyncService ticketingSyncService;

    public TicketingSyncScheduler(TicketingSyncService ticketingSyncService) {
        this.ticketingSyncService = ticketingSyncService;
    }

    /**
     * Tarea programada diaria.
     * Expresión CRON por defecto: "0 0 3 * * ?" (todos los días a las 03:00 AM UTC).
     * Puede personalizarse mediante la propiedad 'ticketing.sync.cron.expression' o TICKETING_CRON_EXPRESSION.
     */
    @Scheduled(cron = "${ticketing.sync.cron.expression:0 0 3 * * ?}", zone = "${ticketing.sync.cron.timezone:UTC}")
    public void executeDailyTicketsSync() {
        log.info("⏰ [CRON DIARIO] Iniciando sincronización programada de stock de entradas para todos los eventos...");
        try {
            TicketingSyncAllSummaryDto summary = ticketingSyncService.syncAllTickets();
            log.info("✅ [CRON DIARIO] Sincronización completada exitosamente: {} eventos exitosos de {}, {} entradas creadas, {} actualizadas.",
                    summary.eventosExitosos(), summary.totalEventosProcesados(),
                    summary.totalEntradasCreadas(), summary.totalEntradasActualizadas());
        } catch (Exception ex) {
            log.error("❌ [CRON DIARIO] Error crítico durante la ejecución de la sincronización programada: {}", ex.getMessage(), ex);
        }
    }
}

