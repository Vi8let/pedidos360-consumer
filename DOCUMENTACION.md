# Documentación Técnica Oficial: Pedidos 360

> **Plataforma de Gestión de Inventario, Catálogo y Despachos Tecnológicos**  
> **Asignatura:** Desarrollo Cloud Native I (DSY1107) — Duoc UC  
> **Evaluaciones:** Evaluación Parcial 3 (EP3) y Evaluación Parcial 4 (EP4)  
> **Versión:** 1.0 (Definitiva)

---

## 1. Resumen Ejecutivo del Proyecto

### 1.1 Visión General
**Pedidos 360** es una plataforma tecnológica empresarial orientada a la administración integral, control de existencias, gestión de pedidos y seguimiento de inventario de componentes y hardware tecnológico de alta demanda (servidores, tarjetas de procesamiento gráfico, periféricos y equipamiento corporativo).

La aplicación aborda la problemática operativa de mantener la consistencia transaccional del stock en tiempo real mientras registra trazas inmutables de auditoría forense, alertas de acceso no autorizado y desvíos de mensajes anómalos sin degradar la latencia percibida por el usuario final.

### 1.2 Objetivos de la Arquitectura
* **Desacoplamiento Estricto:** Separación de la lógica transaccional de negocio (creación, edición y baja de productos) respecto al procesamiento analítico e histórico de auditoría mediante una arquitectura basada en **Microservicios**.
* **Consistencia Eventual y Entrega Garantizada:** Implementación del patrón de diseño **Transactional Outbox**, asegurando una semántica de entrega *At-Least-Once* que previene la pérdida de mensajes incluso ante caídas abruptas del broker de mensajería.
* **Seguridad y Control de Acceso Basado en Roles (RBAC):** Autenticación federada basada en estándares abiertos (OAuth 2.0 / OpenID Connect) mediante **AWS Cognito**, validando tokens JWT e inspeccionando claims de grupo (`Admin`) tanto en el API Gateway como en el Resource Server de Spring Security.
* **Tolerancia a Fallos y Resiliencia:** Topología robusta en **RabbitMQ** incorporando Dead Letter Exchanges (DLX) y Dead Letter Queues (DLQ), confirmaciones manuales de lectura (ACK/NACK) y política estricta de descarte controlado (`requeue = false`) para evitar loops infinitos de reencolamiento.

---

## 2. Arquitectura Global del Sistema

### 2.1 Flujo de Datos End-to-End
El sistema opera a través de un flujo coordinado entre componentes Cloud y servicios contenerizados:

```
[ Frontend Angular ] (localhost:4200)
        │
        ├── 1. Autenticación OAuth2 / PKCE ──► [ AWS Cognito User Pool ]
        │                                             │
        │   ◄── Recibe Token JWT Bearer (Groups) ─────┘
        │
        ├── 2. Petición HTTP + Authorization: Bearer
        ▼
[ AWS API Gateway + Custom Authorizer ]
        │   (Valida firma criptográfica, vigencia y scopes del JWT)
        ▼
[ Proxy Inverso Seguro ]
        ▼
┌────────────────────────── AWS EC2 / Docker Compose Orchestration ──────────────────────────┐
│                                                                                            │
│   ┌────────────────────────────────┐                 ┌─────────────────────────────────┐   │
│   │ Microservicio Productor        │                 │ RabbitMQ Broker                 │   │
│   │ (pedidos360-backend :8888)     │                 │ (Puertos 5672 AMQP / 15672 UI)  │   │
│   │                                │                 │                                 │   │
│   │ • Spring Security RBAC         │── 4. Outbox ───►│ • Topic: pedidos.auditoria.topic│   │
│   │ • API REST Pedidos & Admin     │   Publisher     │ • Direct: pedidos.seguridad.dir │   │
│   │ • OutboxPublisherWorker        │                 │ • DLX: pedidos.dlx              │   │
│   └───────────────┬────────────────┘                 └────────────────┬────────────────┘   │
│                   │                                                   │                    │
│          Transacción ACID                                    5. Consumo Asíncrono          │
│                   ▼                                         (Manual ACK / NACK)            │
│   ┌────────────────────────────────┐                                  ▼                    │
│   │ PostgreSQL (pedidos360_db)     │                 ┌─────────────────────────────────┐   │
│   │ • pedidos                      │                 │ Microservicio Consumidor        │   │
│   │ • outbox_eventos               │                 │ (pedidos360-consumer :8889)     │   │
│   └────────────────────────────────┘                 │                                 │   │
│                                                      │ • InventarioConsumer            │   │
│                                                      │ • EliminacionesConsumer         │   │
│                                                      │ • SeguridadAlertasConsumer      │   │
│                                                      └────────────────┬────────────────┘   │
│                                                                       │                    │
│                                                               Persistencia Aislada         │
│                                                                       ▼                    │
│                                                      ┌─────────────────────────────────┐   │
│                                                      │ PostgreSQL (auditoria_db)       │   │
│                                                      │ • auditoria_inventario          │   │
│                                                      │ • historico_bajas               │   │
│                                                      │ • auditoria_seguridad           │   │
│                                                      │ • rabbit_mensajes_fallidos      │   │
│                                                      └─────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Despliegue en AWS EC2 Orquestado con Docker Compose
La infraestructura de ejecución se hostea en una instancia virtual **Ubuntu Server (AWS EC2)**. Todos los componentes de backend y bases de datos se gestionan mediante Docker Compose, garantizando aislamiento de redes y persistencia de volúmenes:
- **Red común `pedidos360-net`:** Comunicación interna de baja latencia entre el backend, el broker y las bases de datos.
- **Volúmenes nombrados:** `postgres_data` y `rabbitmq_data` protegen la integridad física de los datos contra reinicios del host.

---

## 3. Seguridad y Control de Acceso (AWS Cognito + JWT)

### 3.1 Configuración de AWS Cognito
- **User Pool ID:** `us-east-1_C1DQHPBOn`
- **Mecanismo de Login:** Hosted UI / Managed Login Pages de Cognito.
- **Grupos de Seguridad:**
  - Grupo `Admin`: Asignado a los administradores de catálogo para otorgar privilegios de escritura y eliminación.
- **Scopes Personalizados (Resource Server en Cognito):**
  - `pedidos360/pedidos.read`: Permiso para consultar catálogo.
  - `pedidos360/pedidos.write`: Permiso para crear y modificar inventario.
  - `pedidos360/admin.manage`: Permiso administrativo sobre la topología de colas.

### 3.2 Conversor de Roles (`CognitoRoleConverter.java`)
Para sincronizar las identidades de AWS con el ecosistema de Spring Security, se implementó un conversor personalizado (`Converter<Jwt, AbstractAuthenticationToken>`):
1. Extrae el array de grupos desde el claim propietario `cognito:groups`.
2. Mapea cada grupo al formato de autoridad estándar de Spring Security anteponiendo el prefijo `ROLE_` (ej. `Admin` $\rightarrow$ `ROLE_Admin`).
3. Mantiene en paralelo los scopes OAuth2 (`SCOPE_pedidos360/...`) y establece el identificador único `sub` como nombre principal de la sesión.

### 3.3 Reglas de Autorización y Respuestas HTTP
En `SecurityConfig.java`:
- `GET /api/pedidos/**`: Accesible para cualquier usuario autenticado (`authenticated()`).
- `POST /api/pedidos/**`: Restringido exclusivamente a usuarios con rol `ROLE_Admin`.
- `PUT /api/pedidos/**`: Restringido exclusivamente a usuarios con rol `ROLE_Admin`.
- `DELETE /api/pedidos/**`: Restringido exclusivamente a usuarios con rol `ROLE_Admin`.
- **Manejo de Respuestas HTTP:**
  - `401 Unauthorized` (`CustomAuthenticationEntryPoint`): Se emite ante peticiones sin cabecera `Authorization: Bearer <token>` o con tokens expirados/inválidos.
  - `403 Forbidden` (`CustomAccessDeniedHandler`): Se emite cuando un usuario autenticado sin el rol `Admin` intenta ejecutar `POST`, `PUT` o `DELETE`. Además, este handler genera de forma asíncrona una alerta de seguridad hacia la tabla Outbox (`REQUIRES_NEW`).

---

## 4. Topología y Configuración de Mensajería (RabbitMQ)

### 4.1 Centralización de Constantes (`RabbitNames.java`)
Para evitar cadenas literales dispersas (antipatrón *Magic Strings*) y cumplir estrictamente el **Indicador 1 de la rúbrica EP3**, todos los identificadores del broker residen centralizados:

```java
public final class RabbitNames {
    // Exchanges
    public static final String TOPIC_EXCHANGE   = "pedidos.auditoria.topic";
    public static final String DIRECT_EXCHANGE  = "pedidos.seguridad.direct";
    public static final String DLX_EXCHANGE     = "pedidos.dlx";

    // Colas Principales
    public static final String QUEUE_INVENTARIO    = "cola.auditoria.inventario";
    public static final String QUEUE_ELIMINACIONES = "cola.auditoria.eliminaciones";
    public static final String QUEUE_SEGURIDAD     = "cola.seguridad.alertas";

    // Colas Dead Letter (DLQ)
    public static final String DLQ_INVENTARIO    = "cola.auditoria.inventario.dlq";
    public static final String DLQ_ELIMINACIONES = "cola.auditoria.eliminaciones.dlq";
    public static final String DLQ_SEGURIDAD     = "cola.seguridad.alertas.dlq";

    // Routing Keys
    public static final String RK_INVENTARIO_PATTERN = "inventario.producto.*";
    public static final String RK_ELIMINACIONES_PATTERN = "bajas.producto.*";
    public static final String RK_PRODUCTO_CREADO    = "inventario.producto.creado";
    public static final String RK_PRODUCTO_MODIFICADO= "inventario.producto.modificado";
    public static final String RK_PRODUCTO_ELIMINADO = "bajas.producto.eliminado";
    public static final String RK_SEGURIDAD          = "seguridad.acceso_denegado";
    public static final String RK_DLQ_INVENTARIO     = "dlq.auditoria.inventario";
    public static final String RK_DLQ_ELIMINACIONES  = "dlq.auditoria.eliminaciones";
    public static final String RK_DLQ_SEGURIDAD      = "dlq.seguridad.alertas";
}
```

### 4.2 Declaración de Infraestructura (`RabbitConfig.java`)
Define explícitamente mediante beans de Spring AMQP:
1. **Topic Exchange (`pedidos.auditoria.topic`):** Enruta eventos de inventario y bajas utilizando comodines (`inventario.producto.*` y `bajas.producto.*`).
2. **Direct Exchange (`pedidos.seguridad.direct`):** Enruta alertas de seguridad hacia `cola.seguridad.alertas` usando coincidencia exacta con `seguridad.acceso_denegado`.
3. **Dead Letter Exchange (`pedidos.dlx`):** Direct exchange durable encargado de recibir mensajes rechazados o fallidos.
4. **Colas Principales con DLX configurado:** Cada cola se declara con los argumentos nativos de RabbitMQ:
   - `x-dead-letter-exchange: pedidos.dlx`
   - `x-dead-letter-routing-key: dlq.<dominio>`

### 4.3 Confiabilidad del Productor (Publisher Confirms y Returns)
En `application.properties`:
```properties
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true
spring.rabbitmq.template.mandatory=true
```
El worker `OutboxPublisherWorker.java` asocia una instancia de `CorrelationData` a cada envío. Al recibir la confirmación afirmativa del broker (`confirm.ack() == true`), actualiza el registro en `outbox_eventos` al estado `PUBLICADO`. Si el broker devuelve un NACK, incrementa el contador de reintentos (`reintentos++`) y marca el evento como `FALLIDO` tras 5 intentos fallidos.

### 4.4 Panel Web y API de Administración REST
- **Interfaz Web RabbitMQ Management:** Accesible en el puerto `15672` (credenciales `guest:guest` o parametrizadas en `.env`).
- **Módulo REST de Gestión de Colas (`RabbitAdminController.java`):**
  - `POST /api/admin/queues`: Crea colas dinámicamente mediante `AmqpAdmin`, validando nombres con expresiones regulares (`@Pattern`). Retorna `201 Created`.
  - `DELETE /api/admin/queues/{nombre}`: Elimina la cola indicada del broker. Retorna `200 OK` o `404 Not Found`.

---

## 5. Resguardo, Auditoría y Tolerancia a Fallos (ACK / NACK y DLQ)

### 5.1 Consumo con ACK Manual e Idempotencia
En el microservicio `pedidos360-consumer`, la fábrica de contenedores se configuró en modo de confirmación manual:
```properties
spring.rabbitmq.listener.simple.acknowledge-mode=manual
```
Cada consumidor (`InventarioConsumer`, `EliminacionesConsumer`, `SeguridadAlertasConsumer`):
1. Recibe el mensaje y el `deliveryTag` del canal AMQP.
2. Extrae el `eventoId` (UUID) del payload JSON.
3. **Control de Idempotencia:** Invoca a `repository.existsByEventoId(eventoId)`. Si el evento ya fue procesado con anterioridad, emite `channel.basicAck(deliveryTag, false)` inmediatamente y descarta el duplicado, evitando inconsistencias por reentregas de red.
4. Persiste la información en la tabla correspondiente de `auditoria_db`.
5. Emite la confirmación positiva: `channel.basicAck(deliveryTag, false)`.

### 5.2 Desvío Controlado a Dead Letter Queue (NACK sin Reencole)
Ante payloads con formato JSON corrupto, entidades truncadas o excepciones no recuperables de base de datos, el bloque `catch` ejecuta:
```java
channel.basicNack(deliveryTag, false, false);
```
Al establecer el tercer parámetro (`requeue`) en `false`, RabbitMQ no reintenta entregar el mensaje a la cola principal, sino que lo transfiere inmediatamente a `pedidos.dlx`, depositándolo en su respectiva **Dead Letter Queue (DLQ)** preservando los encabezados de diagnóstico `x-death` con la causa del rechazo.

---

## 6. Modelo de Base de Datos y Persistencia Separada

Para asegurar aislamiento de dominios y cumplir con las mejores prácticas Cloud Native, el motor PostgreSQL 16 administra dos bases de datos lógicas totalmente independientes:

### 6.1 Base de Datos Operacional: `pedidos360_db`
Utilizada exclusivamente por el **Microservicio Productor (`pedidos360-backend`)**:
* **Tabla `pedidos`:**
  - `id` (BIGSERIAL, PK): Identificador único del producto en inventario.
  - `producto` (VARCHAR 255): Denominación del producto tecnológico.
  - `image_url` (TEXT): Enlace a la imagen descriptiva.
  - `cantidad` (INT): Cantidad asociada en el pedido actual.
  - `stock` (INT): Stock disponible en bodega.
  - `estado` (VARCHAR 50): Estado de la orden (`PENDIENTE`, `CONFIRMADO`, `CANCELADO`).
* **Tabla `outbox_eventos`:**
  - `id` (UUID, PK): Identificador global único del evento.
  - `aggregate_type` (VARCHAR 100): Tipo de agregado (`PRODUCTO`, `SEGURIDAD`).
  - `aggregate_id` (VARCHAR 100): Clave del registro de negocio.
  - `tipo_evento` (VARCHAR 100): Routing key de destino.
  - `exchange` (VARCHAR 150): Exchange asignado en RabbitMQ.
  - `routing_key` (VARCHAR 150): Clave de enrutamiento.
  - `payload` (TEXT): Representación JSON completa del evento.
  - `estado` (VARCHAR 50): `PENDIENTE`, `PUBLICADO` o `FALLIDO`.
  - `reintentos` (INT): Contador de intentos de publicación.
  - `creado_en` y `publicado_en` (TIMESTAMP): Marcas de tiempo.

### 6.2 Base de Datos de Trazabilidad: `auditoria_db`
Utilizada exclusivamente por el **Microservicio Consumidor (`pedidos360-consumer`)**:
* **Tabla `auditoria_inventario`:** Historial inmutable de creaciones y actualizaciones de inventario (`evento_id UNIQUE`).
* **Tabla `historico_bajas`:** Historial de productos eliminados, reteniendo el JSON con la instantánea completa (*snapshot*) del producto antes de su borrado físico.
* **Tabla `auditoria_seguridad`:** Registro de incidentes de seguridad e intentos de acceso no autorizados (HTTP 403 Forbidden).
* **Tabla `rabbit_mensajes_fallidos`:** Bitácora para almacenamiento y análisis forense de mensajes desviados a las DLQs.

---

## 7. Guía de Despliegue y Ejecución Local / Cloud

### 7.1 Estructura del Ecosistema de Repositorios
El proyecto se encuentra desacoplado en carpetas hermanas independientes:
```text
C:\DUOC 2026\Desarrollo Cloud Native I 003V\
├── pedidos360-backend\        # Microservicio Productor (Spring Boot :8888)
├── pedidos360-consumer\       # Microservicio Consumidor (Spring Boot :8889)
└── pedidos360-front\          # Frontend SPA (Angular :4200)
```

### 7.2 Levantar Infraestructura con Docker Compose
Tanto en local como en la instancia EC2 de AWS:

1. **Configurar variables de entorno:**
   Verificar que el archivo `.env` contenga las credenciales requeridas:
   ```env
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres
   RABBITMQ_USER=guest
   RABBITMQ_PASSWORD=guest
   ```

2. **Iniciar los servicios contenerizados:**
   ```bash
   # En servidor EC2 con archivo de entorno centralizado:
   sudo docker-compose --env-file /etc/pedidos360/pedidos360.env up -d

   # O localmente desde la carpeta pedidos360-backend:
   docker compose up -d
   ```

3. **Verificar estado de los contenedores:**
   ```bash
   docker ps
   # Deben estar activos: pedidos360-postgres (5432) y pedidos360-rabbitmq (5672, 15672)
   ```

### 7.3 Ejecución de los Microservicios Spring Boot

* **Iniciar Microservicio Productor (`pedidos360-backend`):**
  ```bash
  cd pedidos360-backend
  ./mvnw spring-boot:run
  # Escuchando en http://localhost:8888
  ```

* **Iniciar Microservicio Consumidor (`pedidos360-consumer`):**
  ```bash
  cd pedidos360-consumer
  ./mvnw spring-boot:run
  # Escuchando en http://localhost:8889
  ```

### 7.4 Ejecución de la Batería Completa de Pruebas Unitarias

* **Pruebas del Backend Productor (40 pruebas):**
  ```bash
  cd pedidos360-backend
  ./mvnw clean test
  # Valida: Seguridad RBAC (403/401), PedidoService con Outbox, Worker con Confirms y Admin REST API
  ```

* **Pruebas del Consumidor de Auditoría (9 pruebas):**
  ```bash
  cd pedidos360-consumer
  ./mvnw clean test
  # Valida: InventarioConsumer, EliminacionesConsumer y SeguridadAlertasConsumer con ACK, Idempotencia y desvío a DLQ
  ```

**Total verificado:** **49 pruebas automatizadas con 0 fallos (`BUILD SUCCESS`).**

---

## 8. Clúster de Alta Disponibilidad RabbitMQ (Preparación EP4)

Para la demostración de la Evaluación Parcial 4 (EP4), se incluye la especificación de un clúster de dos nodos con réplica automática:
* **Archivo de orquestación:** `infraestructura/compose.cluster.yaml`
* **Script de unificación de clúster:** `infraestructura/unir-cluster.ps1`
* **Comando de verificación de estado del clúster:**
  ```bash
  docker exec -it rabbit1 rabbitmqctl cluster_status
  ```
  Comprueba que los nodos `rabbit@rabbit1` y `rabbit@rabbit2` se encuentren sincronizados y activos compartiendo la cookie Erlang.
