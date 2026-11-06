package com.pedidos360.consumer.config;

/**
 * Constantes centralizadas de RabbitMQ para el consumidor de auditoria.
 * Coinciden exactamente con la topologia definida en pedidos360-backend.
 */
public final class RabbitNames {

    private RabbitNames() {
        // Constructor privado para impedir instanciacion
    }

    // =========================================================================
    // EXCHANGES
    // =========================================================================
    public static final String TOPIC_EXCHANGE = "pedidos.auditoria.topic";
    public static final String DIRECT_EXCHANGE = "pedidos.seguridad.direct";
    public static final String DLX_EXCHANGE = "pedidos.dlx";

    // =========================================================================
    // COLAS PRINCIPALES
    // =========================================================================
    public static final String QUEUE_INVENTARIO = "cola.auditoria.inventario";
    public static final String QUEUE_ELIMINACIONES = "cola.auditoria.eliminaciones";
    public static final String QUEUE_SEGURIDAD = "cola.seguridad.alertas";

    // Alias oficiales
    public static final String COLA_AUDITORIA_INVENTARIO = QUEUE_INVENTARIO;
    public static final String COLA_AUDITORIA_ELIMINACIONES = QUEUE_ELIMINACIONES;
    public static final String COLA_SEGURIDAD_ALERTAS = QUEUE_SEGURIDAD;

    // =========================================================================
    // COLAS DEAD LETTER (DLQ)
    // =========================================================================
    public static final String DLQ_INVENTARIO = "cola.auditoria.inventario.dlq";
    public static final String DLQ_ELIMINACIONES = "cola.auditoria.eliminaciones.dlq";
    public static final String DLQ_SEGURIDAD = "cola.seguridad.alertas.dlq";

    // =========================================================================
    // ROUTING KEYS
    // =========================================================================
    public static final String RK_INVENTARIO_PATTERN = "inventario.producto.*";
    public static final String RK_ELIMINACIONES_PATTERN = "bajas.producto.*";
    public static final String RK_PRODUCTO_CREADO = "inventario.producto.creado";
    public static final String RK_PRODUCTO_MODIFICADO = "inventario.producto.modificado";
    public static final String RK_PRODUCTO_ELIMINADO = "bajas.producto.eliminado";
    public static final String RK_SEGURIDAD = "seguridad.acceso_denegado";

    public static final String RK_DLQ_INVENTARIO = "dlq.auditoria.inventario";
    public static final String RK_DLQ_ELIMINACIONES = "dlq.auditoria.eliminaciones";
    public static final String RK_DLQ_SEGURIDAD = "dlq.seguridad.alertas";
}

