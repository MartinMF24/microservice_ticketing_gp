package com.uade.microservices.ticketing.repository;

import com.uade.microservices.ticketing.model.EventoF1;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad EventoF1 (tabla public.eventos_f1).
 * Permite resolver el id_evento (UUID) en Supabase a partir de la temporada (año)
 * y el nombre del circuito o ciudad asociados al evento legado.
 */
@Repository
public interface EventoF1Repository extends JpaRepository<EventoF1, UUID> {

    @Query("SELECT e FROM EventoF1 e " +
           "JOIN FETCH e.circuito c " +
           "JOIN FETCH c.ciudad ci " +
           "ORDER BY e.fechaInicio ASC")
    List<EventoF1> findAllWithCircuitoAndCiudad();

    @Query("SELECT e FROM EventoF1 e " +
           "JOIN FETCH e.circuito c " +
           "JOIN FETCH c.ciudad ci " +
           "WHERE e.temporada = :temporada " +
           "ORDER BY e.fechaInicio ASC")
    List<EventoF1> findByTemporadaWithCircuitoAndCiudad(@Param("temporada") Integer temporada);

    @Query("SELECT e FROM EventoF1 e " +
           "JOIN FETCH e.circuito c " +
           "JOIN FETCH c.ciudad ci " +
           "WHERE e.idEvento = :idEvento")
    Optional<EventoF1> findByIdWithCircuitoAndCiudad(@Param("idEvento") UUID idEvento);

    @Query("SELECT e FROM EventoF1 e " +
           "JOIN FETCH e.circuito c " +
           "JOIN FETCH c.ciudad ci " +
           "WHERE e.temporada = :temporada " +
           "AND (LOWER(ci.nombre) = LOWER(:termino) " +
           "  OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "  OR LOWER(ci.nombre) LIKE LOWER(CONCAT('%', :termino, '%')))")
    List<EventoF1> findByTemporadaAndTermino(
            @Param("temporada") Integer temporada,
            @Param("termino") String termino
    );
}
