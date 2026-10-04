package com.uade.microservices.ticketing;

import com.uade.microservices.ticketing.model.EntradaGrada;
import com.uade.microservices.ticketing.repository.EntradaGradaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScratchDbTest {

    @Autowired
    private EntradaGradaRepository entradaGradaRepository;

    @Test
    void inspectAllTickets() {
        List<EntradaGrada> tickets = entradaGradaRepository.findAll();
        System.out.println("=== TODAS LAS ENTRADAS EN SUPABASE (" + tickets.size() + ") ===");
        for (EntradaGrada eg : tickets) {
            System.out.printf("idEvento: %s | Tribuna: %-30s | Stock: %-5d | Precio: %s%n",
                    eg.getIdEvento(), eg.getNombreTribuna(), eg.getStockDisponible(), eg.getPrecioUsd());
        }
        System.out.println("==================================================");
    }
}
