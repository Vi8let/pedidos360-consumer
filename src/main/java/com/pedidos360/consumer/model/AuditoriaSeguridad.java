package com.pedidos360.consumer.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad que almacena alertas de seguridad (p. ej. intentos de acceso denegado 403 Forbidden).
 * Persistida en la base de datos 'auditoria_db', tabla 'auditoria_seguridad'.
 * Restriccion UNIQUE sobre evento_id para garantizar idempotencia estricta.
 */
@Entity
@Table(name = "auditoria_seguridad",
       uniqueConstraints = @UniqueConstraint(name = "uk_auditoria_seguridad_evento_id",
                                             columnNames = "evento_id"))
public class AuditoriaSeguridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(name = "tipo_evento", nullable = false, length = 100)
    private String tipoEvento;

    @Column(name = "usuario_sub", length = 100)
    private String usuarioSub;

    @Column(name = "usuario_email", length = 150)
    private String usuarioEmail;

    @Column(name = "ruta", length = 255)
    private String ruta;

    @Column(name = "metodo", length = 20)
    private String metodo;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "fecha_evento", length = 50)
    private String fechaEvento;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    public AuditoriaSeguridad() {
        this.procesadoEn = Instant.now();
    }

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getUsuarioSub() {
        return usuarioSub;
    }

    public void setUsuarioSub(String usuarioSub) {
        this.usuarioSub = usuarioSub;
    }

    public String getUsuarioEmail() {
        return usuarioEmail;
    }

    public void setUsuarioEmail(String usuarioEmail) {
        this.usuarioEmail = usuarioEmail;
    }

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }

    public String getMetodo() {
        return metodo;
    }

    public void setMetodo(String metodo) {
        this.metodo = metodo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(String fechaEvento) {
        this.fechaEvento = fechaEvento;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }

    public void setProcesadoEn(Instant procesadoEn) {
        this.procesadoEn = procesadoEn;
    }
}

