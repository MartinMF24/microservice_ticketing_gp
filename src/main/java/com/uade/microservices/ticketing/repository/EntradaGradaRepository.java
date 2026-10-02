package com.uade.microservices.ticketing.repository;

import com.uade.microservices.ticketing.model.EntradaGrada;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad EntradaGrada (tabla public.entradas_gradas).
 */
@Repository
public interface EntradaGradaRepository extends JpaRepository<EntradaGrada, UUID> {

    /**
     * Busca una entrada de grada existente por el ID del evento y el nombre de la tribuna (insensible a mayúsculas/minúsculas).
     * Utilizado para implementar la lógica de Upsert.
     */
    Optional<EntradaGrada> findByIdEventoAndNombreTribunaIgnoreCase(UUID idEvento, String nombreTribuna);

    /**
     * Retorna todas las entradas registradas para un evento de F1 específico.
     */
    List<EntradaGrada> findByIdEvento(UUID idEvento);

    /**
     * Verifica si existe alguna entrada para el evento dado.
     */
    boolean existsByIdEvento(UUID idEvento);
}
