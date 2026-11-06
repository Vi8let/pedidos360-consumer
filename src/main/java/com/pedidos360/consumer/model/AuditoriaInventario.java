package com.pedidos360.consumer.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad que almacena los eventos de auditoria de inventario (creaciones y modificaciones).
 * Persistida en la base de datos 'auditoria_db', tabla 'auditoria_inventario'.
 * Restriccion UNIQUE sobre evento_id para garantizar idempotencia estricta.
 */
@Entity
@Table(name = "auditoria_inventario",
       uniqueConstraints = @UniqueConstraint(name = "uk_auditoria_inventario_evento_id",
                                             columnNames = "evento_id"))
public class AuditoriaInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(name = "tipo_evento", nullable = false, length = 100)
    private String tipoEvento;

    @Column(name = "accion", length = 50)
    private String accion;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "nombre_producto", length = 200)
    private String nombreProducto;

    @Column(name = "cantidad")
    private int cantidad;

    @Column(name = "stock")
    private int stock;

    @Column(name = "estado", length = 50)
    private String estado;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "usuario_sub", length = 100)
    private String usuarioSub;

    @Column(name = "usuario_email", length = 150)
    private String usuarioEmail;

    @Column(name = "fecha_evento", length = 50)
    private String fechaEvento;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    public AuditoriaInventario() {
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

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
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

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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

