package com.uade.microservices.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada principal para el microservicio ETL de Ticketing F1 (SOAP -> Supabase PostgreSQL).
 */
@EnableScheduling
@SpringBootApplication
public class TicketingMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketingMicroserviceApplication.class, args);
    }
}
