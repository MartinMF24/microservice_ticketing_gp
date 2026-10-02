package com.uade.microservices.ticketing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad JPA mapeada a la tabla public.ciudades.
 * Permite consultar la ciudad y su relación con circuitos y eventos.
 */
@Entity
@Table(name = "ciudades")
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_ciudad", nullable = false, updatable = false)
    private UUID idCiudad;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "id_pais")
    private UUID idPais;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Ciudad() {
    }

    public Ciudad(UUID idCiudad, String nombre) {
        this.idCiudad = idCiudad;
        this.nombre = nombre;
    }

    public Ciudad(UUID idCiudad, String nombre, UUID idPais, OffsetDateTime createdAt) {
        this.idCiudad = idCiudad;
        this.nombre = nombre;
        this.idPais = idPais;
        this.createdAt = createdAt;
    }

    public UUID getIdCiudad() {
        return idCiudad;
    }

    public void setIdCiudad(UUID idCiudad) {
        this.idCiudad = idCiudad;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public UUID getIdPais() {
        return idPais;
    }

    public void setIdPais(UUID idPais) {
        this.idPais = idPais;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ciudad ciudad = (Ciudad) o;
        return Objects.equals(idCiudad, ciudad.idCiudad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCiudad);
    }

    @Override
    public String toString() {
        return "Ciudad{" +
                "idCiudad=" + idCiudad +
                ", nombre='" + nombre + '\'' +
                '}';
    }
}
