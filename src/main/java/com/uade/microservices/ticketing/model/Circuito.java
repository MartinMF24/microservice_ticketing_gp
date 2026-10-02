package com.uade.microservices.ticketing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad JPA mapeada a la tabla public.circuitos.
 */
@Entity
@Table(name = "circuitos")
public class Circuito {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_circuito", nullable = false, updatable = false)
    private UUID idCircuito;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "longitud_km", precision = 6, scale = 3)
    private BigDecimal longitudKm;

    @Column(name = "curvas")
    private Integer curvas;

    @Column(name = "vueltas")
    private Integer vueltas;

    @Column(name = "mapa_svg_url", columnDefinition = "text")
    private String mapaSvgUrl;

    @Column(name = "id_ciudad", nullable = false)
    private UUID idCiudad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ciudad", insertable = false, updatable = false)
    private Ciudad ciudad;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Circuito() {
    }

    public Circuito(UUID idCircuito, String nombre, UUID idCiudad) {
        this.idCircuito = idCircuito;
        this.nombre = nombre;
        this.idCiudad = idCiudad;
    }

    public UUID getIdCircuito() {
        return idCircuito;
    }

    public void setIdCircuito(UUID idCircuito) {
        this.idCircuito = idCircuito;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getLongitudKm() {
        return longitudKm;
    }

    public void setLongitudKm(BigDecimal longitudKm) {
        this.longitudKm = longitudKm;
    }

    public Integer getCurvas() {
        return curvas;
    }

    public void setCurvas(Integer curvas) {
        this.curvas = curvas;
    }

    public Integer getVueltas() {
        return vueltas;
    }

    public void setVueltas(Integer vueltas) {
        this.vueltas = vueltas;
    }

    public String getMapaSvgUrl() {
        return mapaSvgUrl;
    }

    public void setMapaSvgUrl(String mapaSvgUrl) {
        this.mapaSvgUrl = mapaSvgUrl;
    }

    public UUID getIdCiudad() {
        return idCiudad;
    }

    public void setIdCiudad(UUID idCiudad) {
        this.idCiudad = idCiudad;
    }

    public Ciudad getCiudad() {
        return ciudad;
    }

    public void setCiudad(Ciudad ciudad) {
        this.ciudad = ciudad;
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
        Circuito circuito = (Circuito) o;
        return Objects.equals(idCircuito, circuito.idCircuito);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCircuito);
    }

    @Override
    public String toString() {
        return "Circuito{" +
                "idCircuito=" + idCircuito +
                ", nombre='" + nombre + '\'' +
                '}';
    }
}
