package com.pedidos360.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio Consumidor de Auditoria - Pedidos 360
 *
 * Responsabilidades:
 *  - Escuchar mensajes de RabbitMQ desde las colas de auditoría y seguridad.
 *  - Persistir los eventos en auditoria_db con garantías de idempotencia.
 *  - Confirmar (ACK) o rechazar (NACK → DLQ) mensajes de forma explícita.
 */
@SpringBootApplication
public class Pedidos360ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360ConsumerApplication.class, args);
    }
}

