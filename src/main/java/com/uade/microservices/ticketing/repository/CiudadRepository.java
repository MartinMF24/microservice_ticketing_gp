package com.uade.microservices.ticketing.repository;

import com.uade.microservices.ticketing.model.Ciudad;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad Ciudad (tabla public.ciudades).
 */
@Repository
public interface CiudadRepository extends JpaRepository<Ciudad, UUID> {

    Optional<Ciudad> findFirstByNombreIgnoreCase(String nombre);

    @Query("SELECT c FROM Ciudad c WHERE LOWER(c.nombre) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Ciudad> searchByTermIgnoreCase(@Param("term") String term);
}
