package com.uade.microservices.ticketing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad JPA correspondiente a la tabla public.entradas_gradas.
 * Almacena el inventario de tribunas, precios en USD, stock disponible y categoría de tipo
 * ('VIP', 'Asiento Numerado', 'General') para cada Gran Premio de Fórmula 1.
 */
@Entity
@Table(name = "entradas_gradas")
public class EntradaGrada {

    public static final String TIPO_VIP = "VIP";
    public static final String TIPO_ASIENTO_NUMERADO = "Asiento Numerado";
    public static final String TIPO_GENERAL = "General";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_entrada", nullable = false, updatable = false)
    private UUID idEntrada;

    @Column(name = "id_evento", nullable = false)
    private UUID idEvento;

    @Column(name = "nombre_tribuna", nullable = false, length = 100)
    private String nombreTribuna;

    @Column(name = "precio_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUsd;

    @Column(name = "stock_disponible", nullable = false)
    private Integer stockDisponible = 0;

    @Column(name = "tipo", length = 50)
    private String tipo;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evento", insertable = false, updatable = false)
    private EventoF1 evento;

    public EntradaGrada() {
    }

    public EntradaGrada(UUID idEvento, String nombreTribuna, BigDecimal precioUsd, Integer stockDisponible, String tipo) {
        this.idEvento = idEvento;
        this.nombreTribuna = nombreTribuna;
        this.precioUsd = precioUsd;
        this.stockDisponible = (stockDisponible != null) ? stockDisponible : 0;
        this.tipo = tipo;
        this.createdAt = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.stockDisponible == null) {
            this.stockDisponible = 0;
        }
    }

    public UUID getIdEntrada() {
        return idEntrada;
    }

    public void setIdEntrada(UUID idEntrada) {
        this.idEntrada = idEntrada;
    }

    public UUID getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(UUID idEvento) {
        this.idEvento = idEvento;
    }

    public String getNombreTribuna() {
        return nombreTribuna;
    }

    public void setNombreTribuna(String nombreTribuna) {
        this.nombreTribuna = nombreTribuna;
    }

    public BigDecimal getPrecioUsd() {
        return precioUsd;
    }

    public void setPrecioUsd(BigDecimal precioUsd) {
        this.precioUsd = precioUsd;
    }

    public Integer getStockDisponible() {
        return stockDisponible;
    }

    public void setStockDisponible(Integer stockDisponible) {
        this.stockDisponible = stockDisponible;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public EventoF1 getEvento() {
        return evento;
    }

    public void setEvento(EventoF1 evento) {
        this.evento = evento;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntradaGrada that = (EntradaGrada) o;
        return Objects.equals(idEntrada, that.idEntrada);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idEntrada);
    }

    @Override
    public String toString() {
        return "EntradaGrada{" +
                "idEntrada=" + idEntrada +
                ", idEvento=" + idEvento +
                ", nombreTribuna='" + nombreTribuna + '\'' +
                ", precioUsd=" + precioUsd +
                ", stockDisponible=" + stockDisponible +
                ", tipo='" + tipo + '\'' +
                '}';
    }
}
