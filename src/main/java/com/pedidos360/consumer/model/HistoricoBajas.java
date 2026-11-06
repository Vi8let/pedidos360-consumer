package com.pedidos360.consumer.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad que registra el historico de pedidos eliminados con su snapshot completo.
 * Persistida en la base de datos 'auditoria_db', tabla 'historico_bajas'.
 * Restriccion UNIQUE sobre evento_id para garantizar idempotencia estricta.
 */
@Entity
@Table(name = "historico_bajas",
       uniqueConstraints = @UniqueConstraint(name = "uk_historico_bajas_evento_id",
                                             columnNames = "evento_id"))
public class HistoricoBajas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "nombre_producto", length = 200)
    private String nombreProducto;

    @Column(name = "tipo_evento", nullable = false, length = 100)
    private String tipoEvento;

    @Column(name = "snapshot_pedido", columnDefinition = "TEXT", nullable = false)
    private String snapshotPedido;

    @Column(name = "usuario_sub", length = 100)
    private String usuarioSub;

    @Column(name = "usuario_email", length = 150)
    private String usuarioEmail;

    @Column(name = "fecha_evento", length = 50)
    private String fechaEvento;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    public HistoricoBajas() {
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

    public Long getProductoId() {
        return productoId;
    }

    public void setProductoId(Long productoId) {
        this.productoId = productoId;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getSnapshotPedido() {
        return snapshotPedido;
    }

    public void setSnapshotPedido(String snapshotPedido) {
        this.snapshotPedido = snapshotPedido;
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

    public String getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(String fechaEvento) {
        this.fechaEvento = fechaEvento;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }

    public void setProcesadoEn(Instant procesadoEn) {
        this.procesadoEn = procesadoEn;
    }
}

