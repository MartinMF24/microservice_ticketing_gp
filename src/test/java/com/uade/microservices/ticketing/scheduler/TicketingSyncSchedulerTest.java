package com.uade.microservices.ticketing.scheduler;

import com.uade.microservices.ticketing.dto.response.TicketingSyncAllSummaryDto;
import com.uade.microservices.ticketing.service.TicketingSyncService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketingSyncSchedulerTest {

    @Mock
    private TicketingSyncService ticketingSyncService;

    @InjectMocks
    private TicketingSyncScheduler scheduler;

    @Test
    @DisplayName("El programador CRON debe invocar syncAllTickets en su ejecución periódica")
    void shouldCallSyncAllTickets() {
        TicketingSyncAllSummaryDto mockSummary = new TicketingSyncAllSummaryDto(
                19, 19, 0, 76, 50, 26, OffsetDateTime.now(), List.of()
        );

        when(ticketingSyncService.syncAllTickets()).thenReturn(mockSummary);

        scheduler.executeDailyTicketsSync();

        verify(ticketingSyncService, times(1)).syncAllTickets();
    }
}

