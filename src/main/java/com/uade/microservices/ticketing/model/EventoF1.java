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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad JPA mapeada a la tabla public.eventos_f1.
 * Representa un Gran Premio de una temporada determinada.
 */
@Entity
@Table(name = "eventos_f1")
public class EventoF1 {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_evento", nullable = false, updatable = false)
    private UUID idEvento;

    @Column(name = "temporada", nullable = false)
    private Integer temporada;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "estado")
    private String estado;

    @Column(name = "id_circuito", nullable = false)
    private UUID idCircuito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_circuito", insertable = false, updatable = false)
    private Circuito circuito;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public EventoF1() {
    }

    public EventoF1(UUID idEvento, Integer temporada, LocalDate fechaInicio, LocalDate fechaFin, String estado, UUID idCircuito) {
        this.idEvento = idEvento;
        this.temporada = temporada;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.estado = estado;
        this.idCircuito = idCircuito;
    }

    public UUID getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(UUID idEvento) {
        this.idEvento = idEvento;
    }

    public Integer getTemporada() {
        return temporada;
    }

    public void setTemporada(Integer temporada) {
        this.temporada = temporada;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public UUID getIdCircuito() {
        return idCircuito;
    }

    public void setIdCircuito(UUID idCircuito) {
        this.idCircuito = idCircuito;
    }

    public Circuito getCircuito() {
        return circuito;
    }

    public void setCircuito(Circuito circuito) {
        this.circuito = circuito;
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
        EventoF1 eventoF1 = (EventoF1) o;
        return Objects.equals(idEvento, eventoF1.idEvento);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idEvento);
    }

    @Override
    public String toString() {
        return "EventoF1{" +
                "idEvento=" + idEvento +
                ", temporada=" + temporada +
                ", fechaInicio=" + fechaInicio +
                ", estado='" + estado + '\'' +
                '}';
    }
}
