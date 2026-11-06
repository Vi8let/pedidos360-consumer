# Pedidos 360 - Microservicio Consumidor de Auditoría (`pedidos360-consumer`)

Microservicio desacoplado de auditoría para la plataforma **Pedidos 360** (DUOC UC - Desarrollo Cloud Native I, EP3 & EP4).

## Características

- **Consumo Asíncrono desacoplado**: Escucha eventos desde el broker RabbitMQ mediante `@RabbitListener`.
- **Persistencia Aislada**: Almacena los registros en la base de datos `auditoria_db` (PostgreSQL 16), completamente independiente de `pedidos360_db`.
- **Idempotencia Estricta**: Cada consumidor verifica la unicidad de `eventoId` (`UUID`) antes de persistir, evitando duplicados ante reentregas *At-Least-Once*.
- **Manejo Manual de Confirmaciones (ACK/NACK)**:
  - `basicAck`: Confirmación positiva tras persistencia exitosa o descarte de duplicados.
  - `basicNack(requeue = false)`: Desvío automático sin reencolamiento infinito hacia las **Dead Letter Queues (DLQ)** a través de `pedidos.dlx`.

## Colas y Dominios Funcionales

| Cola Principal | Dominio | Tabla en `auditoria_db` | Dead Letter Queue (DLQ) |
|---|---|---|---|
| `cola.auditoria.inventario` | Creación y actualización de pedidos | `auditoria_inventario` | `cola.auditoria.inventario.dlq` |
| `cola.auditoria.eliminaciones` | Bajas con snapshot completo del producto | `historico_bajas` | `cola.auditoria.eliminaciones.dlq` |
| `cola.seguridad.alertas` | Alertas de acceso no autorizado (HTTP 403) | `auditoria_seguridad` | `cola.seguridad.alertas.dlq` |

## Ejecución

```bash
# Compilar y ejecutar pruebas
./mvnw clean test

# Iniciar el microservicio (puerto 8889)
./mvnw spring-boot:run
```
