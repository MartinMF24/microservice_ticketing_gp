package com.uade.microservices.ticketing.repository;

import com.uade.microservices.ticketing.model.Circuito;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad Circuito (tabla public.circuitos).
 */
@Repository
public interface CircuitoRepository extends JpaRepository<Circuito, UUID> {

    Optional<Circuito> findFirstByNombreIgnoreCase(String nombre);

    List<Circuito> findByIdCiudad(UUID idCiudad);
}
