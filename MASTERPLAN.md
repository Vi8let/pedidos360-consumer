# MASTERPLAN: Sistema Pedidos 360 - Arquitectura Cloud Native

> **Asignatura:** Desarrollo Cloud Native I (DSY1107) — Duoc UC  
> **Alcance Evaluativo:** Evaluación Parcial N° 3 (EP3 - 100% Encargo Técnico) y Evaluación Parcial N° 4 (EP4 - 100% Presentación e Infraestructura Cloud)  
> **Fecha de Creación:** Noviembre 2026  
> **Estado:** Documento Maestro Activo / Línea Base de Implementación

---

## 1. Objetivo General y Alcance

### 1.1 Objetivo

Implementar y consolidar la arquitectura de backend para la plataforma **Pedidos 360** (gestión de inventario y pedidos de productos tecnológicos), incorporando:

- Persistencia relacional definitiva en **PostgreSQL 16**.
- Comunicación asíncrona desacoplada mediante **RabbitMQ** (Topic Exchange, Direct Exchange y Dead Letter Exchange).
- Confiabilidad transaccional mediante el patrón **Transactional Outbox** y entrega _At-Least-Once_.
- Desacoplamiento de responsabilidades mediante un **Microservicio Consumidor de Auditoría** con almacenamiento en base de datos independiente (`auditoria_db`).
- Autenticación federada y control de acceso basado en roles (RBAC) con **AWS Cognito** y **Spring Security**.
- Módulo de administración dinámica del broker mediante API REST (**`RabbitAdminService`**).
- Entorno de ejecución contenerizado con **Docker Compose** preparado para escalado a clúster multirruta.

### 1.2 Alcance del Sistema

- **Dominio de Negocio:** Backoffice corporativo de administración de catálogo y existencias de hardware y equipamiento tecnológico.
- **Operaciones Principales:** Registro (`POST`), consulta general e individual (`GET`), actualización de stock/estado (`PUT`) y baja de inventario (`DELETE`).
- **Límites Explícitos:** No implementa pasarelas de pago, cálculo tributario, checkout público, AWS Lambda ni DynamoDB.

---

## 2. Arquitectura Acordada

```
                                [ Cliente / Frontend / Postman ]
                                               │
                                 1. HTTPS + Authorization: Bearer <JWT>
                                               ▼
                              ┌──────────────────────────────────┐
                              │     AWS Cognito (IDaaS CIAM)     │
                              │  - User Pool: us-east-1_C1DQHPBOn│
                              │  - Grupos: Admin / Standard Users│
                              └──────────────────────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ BACKEND PRINCIPAL (pedidos360-backend : Puerto 8888)                                            │
│                                                                                                 │
│  [ Spring Security ] ── Valida JWT, extrae claim 'sub' y mapea 'cognito:groups' ──► ROLE_Admin  │
│          │                                                                                      │
│          ├── GET /api/pedidos/**  ──────────► Permitido a todo usuario autenticado              │
│          └── POST, PUT, DELETE    ──────────► Exige ROLE_Admin (403 si carece de rol)           │
│                                                                                                 │
│  [ PedidoService ] ── Transacción Atómica ACID                                                  │
│          ├── 1. Persiste cambio en tabla 'pedidos'                                              │
│          └── 2. Inserta evento en tabla 'outbox_eventos' (Estado: PENDIENTE)                    │
│                                                                                                 │
│  [ OutboxPublisherWorker ] ── Proceso en segundo plano                                         │
│          └── Lee eventos PENDIENTES ──► Publica con Publisher Confirms (RabbitTemplate)         │
│                                                                                                 │
│  [ RabbitAdminController & RabbitAdminService ] ── API REST Gestión RabbitMQ (POST/DELETE)      │
└──────────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                               │
                                      AMQP 5672 (Mensajes Persistentes)
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ BROKER RABBITMQ (Docker / Clúster)                                                              │
│                                                                                                 │
│  [ pedidos.auditoria.topic ] (Topic Exchange)                                                   │
│      ├── Binding "inventario.producto.*"  ──► cola.auditoria.inventario                         │
│      └── Binding "bajas.producto.*"       ──► cola.auditoria.eliminaciones                      │
│                                                                                                 │
│  [ pedidos.seguridad.direct ] (Direct Exchange)                                                 │
│      └── Binding "seguridad.acceso_denegado" ──► cola.seguridad.alertas                         │
│                                                                                                 │
│  [ pedidos.dlx ] (Dead Letter Exchange - Direct)                                                │
│      ├── Binding "dlq.auditoria.inventario"    ──► cola.auditoria.inventario.dlq                │
│      ├── Binding "dlq.auditoria.eliminaciones" ──► cola.auditoria.eliminaciones.dlq               │
│      └── Binding "dlq.seguridad.alertas"       ──► cola.seguridad.alertas.dlq                   │
└──────────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                               │
                                  AMQP 5672 (ACK/NACK Manual, Prefetch=1)
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ CONSUMIDOR DE AUDITORÍA (pedidos360-consumer : Independiente)                                   │
│                                                                                                 │
│  [ @RabbitListener ] ── Desacoplado de la lógica de negocio del backend                         │
│      ├── InventarioConsumer   (cola.auditoria.inventario)    ──► Guarda en auditoria_inventario │
│      ├── EliminacionesConsumer(cola.auditoria.eliminaciones) ──► Guarda en historico_bajas      │
│      └── SeguridadConsumer    (cola.seguridad.alertas)       ──► Guarda en auditoria_seguridad  │
│                                                                                                 │
│  Reglas de Consumo:                                                                             │
│      1. Control de Idempotencia: Verifica unicidad por 'evento_id' (UUID).                      │
│      2. Reintentos locales: Máximo 3 intentos con backoff ante fallas transitorias.             │
│      3. Confirmación: 'basicAck' SOLO tras persistencia exitosa en base de datos.               │
│      4. Desvío: 'basicNack(requeue=false)' ante error permanente ──► Enruta a DLQ vía DLX.      │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Modelo de Persistencia: PostgreSQL con Separación Lógica

| Base de Datos Lógica | Microservicio Dueño                | Tablas Principales                                               | Propósito                                                              |
| :------------------- | :--------------------------------- | :--------------------------------------------------------------- | :--------------------------------------------------------------------- |
| **`pedidos360_db`**  | `pedidos360-backend` (Productor)   | `pedidos`, `outbox_eventos`                                      | Datos operacionales del catálogo y cola de salida local transaccional. |
| **`auditoria_db`**   | `pedidos360-consumer` (Consumidor) | `auditoria_inventario`, `historico_bajas`, `auditoria_seguridad` | Almacenamiento histórico inmutable de eventos y trazabilidad forense.  |

---

## 3. Requisitos Funcionales y Técnicos

### 3.1 Base de Datos PostgreSQL 16

- Sustitución total de H2 por PostgreSQL 16 en contenedor Docker.
- Aislamiento lógico estricto: dos bases de datos independientes administradas por el mismo motor (`pedidos360_db` y `auditoria_db`).
- La entidad `Pedido.java` mantiene exactamente sus 6 atributos operacionales: `id`, `producto`, `imageUrl` (`image_url`), `cantidad`, `stock` y `estado`.

### 3.2 Topología y Enrutamiento RabbitMQ

- **Topic Exchange (`pedidos.auditoria.topic`):** Enrutamiento jerárquico segmentado.
  - Evento creación: `inventario.producto.creado`.
  - Evento modificación: `inventario.producto.modificado`.
  - Evento eliminación: `bajas.producto.eliminado`.
  - _Garantía de no solapamiento:_ `inventario.producto.*` solo captura altas y modificaciones; **no** captura `bajas.producto.eliminado`.
- **Direct Exchange (`pedidos.seguridad.direct`):** Enrutamiento determinístico 1 a 1 para violaciones de acceso con routing key `seguridad.acceso_denegado`.
- **Dead Letter Exchange (`pedidos.dlx`):** Direct Exchange dedicado exclusivamente al desvío de mensajes descartados por los consumidores.

### 3.3 Transactional Outbox y Confiabilidad

- **At-Least-Once Delivery:** La persistencia de datos y el registro del evento en `outbox_eventos` ocurren en la misma transacción `@Transactional`.
- **Publisher Confirms:** El publicador no marca un evento como `PUBLICADO` hasta recibir la confirmación ACK de RabbitMQ.
- **Mensajes Devueltos:** Configuración `mandatory=true` y `ReturnsCallback` para capturar publicaciones huérfanas sin ruta.
- **Idempotencia:** Restricción `UNIQUE` sobre `evento_id` en las tablas de auditoría. Si un mensaje duplicado ingresa, se descarta sin duplicar y se emite ACK.
- **Snapshot en Bajas:** En `DELETE /api/pedidos/{id}`, se consulta la entidad completa antes de llamar a `repository.delete()`, serializando sus datos en el evento outbox.

### 3.4 Seguridad y Control de Acceso (Cognito + RBAC)

- Autenticación Resource Server validando firma JWT contra Cognito JWKS.
- Conversor de autoridades (`JwtAuthenticationConverter`): extrae el arreglo `cognito:groups` del JWT y asigna la autoridad `ROLE_Admin`.
- Identificador canónico del actor: claim **`sub`** (UUID inmutable). El claim `email` se captura de forma complementaria y condicional.
- `GET /api/pedidos/**` accesible a cualquier usuario autenticado.
- `POST`, `PUT`, `DELETE /api/pedidos/**` restringidos a `hasRole('Admin')`.
- Peticiones no autenticadas reciben **401 Unauthorized**.
- Peticiones autenticadas sin grupo `Admin` reciben **403 Forbidden** y disparan un evento hacia `pedidos.seguridad.direct`.

### 3.5 Microservicio Administrador de RabbitMQ

- Endpoints REST obligatorios:
  - `POST /api/admin/queues`: creación de colas con parámetros en el cuerpo.
  - `DELETE /api/admin/queues/{name}`: eliminación de colas por nombre.
- Encapsulado obligatorio en `RabbitAdminService` (desacoplado del controlador).
- Validación de entradas con DTOs anotados (`@NotBlank`, `@Pattern`, `@Size`).

---

## 4. Requisitos de la Evaluación EP3 (Ponderaciones Oficiales)

| Indicador Oficial EP3                         | % Ponderación | Criterio de Cumplimiento (100% Desempeño)                                                                              | Componentes Técnicos Responsables             |
| :-------------------------------------------- | :-----------: | :--------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------- |
| **1. Nombres centralizados**                  |    **12%**    | Todos los nombres de colas, exchanges y bindings definidos centralizadamente sin literales dispersos.                  | `config/RabbitNames.java`                     |
| **2. Beans de configuración**                 |    **13%**    | Clases de configuración con `@Bean` explícitos para Queue, Exchange, Binding y DLX para cada caso.                     | `config/RabbitConfig.java`                    |
| **3. Separación de responsabilidades**        |    **10%**    | Configuración de mensajería aislada en clases específicas. Servicios de negocio libres de llamadas de infraestructura. | `config/` vs `service/`                       |
| **4. Consumidores por dominio**               |    **15%**    | Consumidores con `@RabbitListener` organizados por dominio funcional en paquetes específicos.                          | `consumer/` en microservicio auditoría        |
| **5. Confirmación ACK/NACK y DLQ**            |    **20%**    | Manejo explícito de ACK manual, reintentos controlados con backoff, y NACK (`requeue=false`) hacia DLQ con logs.       | Listeners + argumentos `x-dead-letter-*`      |
| **6. Endpoints de administración REST**       |    **13%**    | Endpoints REST bien definidos con verbos correctos (`POST /queues`, `DELETE /queues/{name}`).                          | `admin/controller/RabbitAdminController.java` |
| **7. Servicio de administración encapsulado** |    **10%**    | Lógica de gestión de colas/exchanges encapsulada en `RabbitAdminService` sin lógica en controladores.                  | `admin/service/RabbitAdminService.java`       |
| **8. Validación de parámetros DTO**           |    **7%**     | Validación obligatoria de parámetros con anotaciones (`@Valid`, `@NotBlank`) evitando nombres vacíos.                  | `admin/dto/CrearColaRequest.java`             |
| **TOTAL EVALUACIÓN PARCIAL N° 3**             |   **100%**    | **Cumplimiento integral demostrable en código fuente y pruebas.**                                                      |                                               |

---

## 5. Requisitos de la Evaluación EP4 y Demostración en Nube

La entrega de EP3 debe sentar las bases directas para la defensa presencial de EP4:

1. **API Manager y Endpoints:** Configuración de API Gateway enrutando peticiones hacia el backend.
2. **Consumo desde Frontend:** Demostración de Angular invocando endpoints a través del API Gateway.
3. **Validación JWT:** Evidencia de rechazo (401/403) con token inválido/expirado/sin rol, y aceptación (200/201) con token válido.
4. **Tenant e IDaaS:** Usuarios y grupo `Admin` configurados en Amazon Cognito.
5. **Frontend y Backend Desplegados:** Servicios operativos e integrados en AWS (EC2 / contenedores).
6. **Clúster de 2 Nodos RabbitMQ:** Evidencia técnica mediante `rabbitmqctl cluster_status` comprobando que ambos contenedores comparten cookie Erlang y forman un clúster activo.
7. **3 Colas Principales con 3 DLQs:** Verificación visual en RabbitMQ Management de las 6 colas y su flujo de mensajes.
8. **Exchanges Direct y Topic:** Demostración en vivo del enrutamiento selectivo y dead-lettering.
9. **Docker Compose en Servidor Cloud:** Demostración de los servicios levantados en la instancia virtual mediante Docker.

---

## 6. Decisiones Técnicas y Restricciones

### 6.1 Decisiones Técnicas Aprobadas

- **D1 - Motor de Base de Datos:** PostgreSQL 16 oficial (`postgres:16-alpine`), con script de inicialización `/docker-entrypoint-initdb.d/init-databases.sh` para crear `pedidos360_db` y `auditoria_db`.
- **D2 - Aislamiento del Consumidor:** El consumidor se implementa como un microservicio Spring Boot independiente (`pedidos360-consumer`), con su propia conexión a `auditoria_db` y su propio puerto/proceso.
- **D3 - Resiliencia del Productor:** El backend utiliza Transactional Outbox. Si RabbitMQ está caído, la operación de inventario no se revierte; el evento queda retenido en PostgreSQL y se recupera automáticamente al iniciar el broker.
- **D4 - Mapeo de Identidad:** El usuario de auditoría se extrae de `sub` (garantizado en JWT). El `email` se almacena condicionalmente si está presente.
- **D5 - Administración de RabbitMQ:** Implementación híbrida robusta con `AmqpAdmin` de Spring AMQP para operaciones nativas AMQP y/o `RestClient` contra RabbitMQ Management HTTP API.

### 6.2 Restricciones Mandatorias

- **R1:** Prohibido modificar clases Java o configuraciones funcionales sin validación previa del Masterplan.
- **R2:** Prohibido el uso de H2 en la arquitectura final.
- **R3:** Prohibido almacenar contraseñas, secretos o tokens JWT íntegros en bases de datos o logs.
- **R4:** Prohibido el uso de `requeue=true` ante errores permanentes en los consumidores.
- **R5:** Mantener estricta compatibilidad con las versiones vigentes del proyecto (Java 17, Spring Boot 4.x / Spring Framework 7.x).

---

## 7. Plan de Implementación por Fases

```
[ FASE 1: Infraestructura Base ]
  ├── docker-compose.yml (PostgreSQL + RabbitMQ)
  └── Scripts de inicialización multi-database
            │
            ▼
[ FASE 2: Adaptación del Backend Productor ]
  ├── Configuración pom.xml (PostgreSQL, AMQP, Validation)
  ├── Configuración application.properties (pedidos360_db)
  └── Entidad y Repositorio Outbox
            │
            ▼
[ FASE 3: Seguridad y Autorización RBAC ]
  ├── JwtAuthenticationConverter (cognito:groups -> ROLE_Admin)
  ├── SecurityConfig con matchers por rol (GET vs POST/PUT/DELETE)
  └── Handlers para 401 y 403 con emisión de alertas
            │
            ▼
[ FASE 4: Topología RabbitMQ y Transactional Outbox ]
  ├── RabbitNames.java y RabbitConfig.java (Beans Topic, Direct, DLX, Bindings)
  ├── Modificación de PedidoService (Registro atómico de Outbox y snapshot de borrado)
  └── OutboxPublisherWorker (Envío con Publisher Confirms)
            │
            ▼
[ FASE 5: Módulo de Administración REST (EP3) ]
  ├── DTOs validados (@NotBlank, @Pattern)
  ├── RabbitAdminService (Lógica de creación/borrado de colas)
  └── RabbitAdminController (Endpoints POST /queues y DELETE /queues/{name})
            │
            ▼
[ FASE 6: Microservicio Consumidor de Auditoría ]
  ├── Estructura independiente (pedidos360-consumer)
  ├── Conexión a auditoria_db
  ├── Entidades de auditoría con restricción UNIQUE en evento_id
  └── Listeners con ACK manual, reintentos controlados y desvío a DLQ
            │
            ▼
[ FASE 7: Pruebas Integrales y Verificación de Rúbricas ]
  ├── Batería de pruebas locales (CRUD, Seguridad 403, Outbox, RabbitMQ, DLQ)
  └── Preparación de scripts para clúster EP4
```

---

## 8. Lista Maestra de Tareas de Implementación

### Fase 1: Infraestructura y Contenedores

- [x] `INF-01`: Diseñar especificación de `docker-compose.yml` con servicios `postgres` y `rabbitmq`.
- [x] `INF-02`: Crear `.env.example` y `.env` para parametrizar credenciales locales.
- [x] `INF-03`: Crear `infraestructura/init-databases.sh` e `init-databases.sql` para crear `pedidos360_db` y `auditoria_db`.
- [ ] `INF-04`: Verificar ejecución de contenedores Docker y comprobar accesibilidad de puertos 5432, 5672 y 15672.

### Fase 2: Persistencia PostgreSQL en Backend Principal

- [x] `BD-01`: Actualizar `pom.xml` en `pedidos360-backend`: agregar `org.postgresql:postgresql`, `spring-boot-starter-amqp`, `spring-boot-starter-validation`, y remover `h2`.
- [x] `BD-02`: Actualizar `application.properties` con cadena de conexión a `pedidos360_db` y configuración de RabbitMQ.
- [x] `BD-03`: Validar que la entidad `Pedido.java` mantenga exactamente sus 6 atributos operacionales y sea compatible con PostgreSQL.
- [x] `BD-04`: Crear entidad `OutboxEvento.java` y repositorio `OutboxEventoRepository.java` en `pedidos360-backend`.
- [x] `BD-05`: Crear archivo de migración o script DDL para inicializar tablas `pedidos` y `outbox_eventos` (`infraestructura/ddl-pedidos360_db.sql` y `ddl-auditoria_db.sql`).

### Fase 3: Seguridad, Cognito y Control de Acceso (RBAC)

- [x] `SEC-01`: Crear clase `CognitoRoleConverter.java` (`JwtAuthenticationConverter`) para transformar `cognito:groups` en `ROLE_Admin`.
- [x] `SEC-02`: Actualizar `SecurityConfig.java`: restringir `GET` a usuarios autenticados y `POST`, `PUT`, `DELETE` a `hasRole('Admin')`.
- [x] `SEC-03`: Implementar `CustomAccessDeniedHandler` para capturar HTTP 403, extraer usuario (`sub`) y emitir evento de seguridad.
- [x] `SEC-04`: Implementar `CustomAuthenticationEntryPoint` para capturar HTTP 401 y registrar intentos no autenticados.
- [x] `SEC-05`: Crear pruebas unitarias con `@WithMockUser` para verificar respuesta 403 ante mutaciones sin rol Admin.

### Fase 4: Topología RabbitMQ y Transactional Outbox

- [x] `RAB-01`: Crear clase centralizada de constantes `cl.duoc.pedidos360_backend.config.RabbitNames.java`.
- [x] `RAB-02`: Crear clase `@Configuration` `RabbitConfig.java` declarando beans de `TopicExchange`, `DirectExchange`, `DirectExchange DLX`, Colas principales, Colas DLQ y Bindings.
- [x] `RAB-03`: Configurar en `RabbitConfig.java` los argumentos de cola `x-dead-letter-exchange` y `x-dead-letter-routing-key`.
- [x] `RAB-04`: Modificar `PedidoService.java`: en `crearPedido()`, `actualizarPedido()` y `eliminarPedido()`, persistir el evento en `outbox_eventos` dentro de la transacción.
- [x] `RAB-05`: Asegurar que en `eliminarPedido(id)` se conserve la instantánea completa del producto antes de ejecutar `delete()`.
- [x] `RAB-06`: Implementar `OutboxPublisherWorker.java` con `@Scheduled`, `RabbitTemplate`, Publisher Confirms y manejo de devoluciones (`ReturnsCallback`).

### Fase 5: Módulo de Administración REST (Indicadores EP3: 6, 7 y 8)

- [x] `ADM-01`: Crear DTOs de solicitud `CrearColaRequest.java`, `CrearExchangeRequest.java` con validaciones `@NotBlank` y `@Pattern`.
- [x] `ADM-02`: Implementar servicio `RabbitAdminService.java` encapsulando la creación y eliminación de colas (`AmqpAdmin` / `RestClient`).
- [x] `ADM-03`: Implementar controlador REST `RabbitAdminController.java` exponiendo `POST /api/admin/queues` y `DELETE /api/admin/queues/{nombre}`.
- [x] `ADM-04`: Probar manejo de excepciones y respuestas HTTP adecuadas (201 Created, 200 OK, 400 Bad Request, 404 Not Found).

### Fase 6: Microservicio Consumidor de Auditoría (`pedidos360-consumer`)

- [x] `CON-01`: Crear estructura del microservicio independiente `pedidos360-consumer` con su propio `pom.xml` y clase de arranque en repositorio separado (`C:\DUOC 2026\Desarrollo Cloud Native I 003V\pedidos360-consumer`).
- [x] `CON-02`: Configurar `application.properties` del consumidor conectando a `auditoria_db` y puerto independiente (8889).
- [x] `CON-03`: Crear entidades JPA con restricción única sobre `evento_id`: `AuditoriaInventario`, `HistoricoBajas` y `AuditoriaSeguridad`.
- [x] `CON-04`: Crear repositorios JPA con método `existsByEventoId(UUID eventoId)` para garantizar idempotencia.
- [x] `CON-05`: Implementar `InventarioConsumer.java` escuchando `cola.auditoria.inventario` con ACK manual e idempotencia.
- [x] `CON-06`: Implementar `EliminacionesConsumer.java` escuchando `cola.auditoria.eliminaciones` con ACK manual y almacenamiento de snapshot completo.
- [x] `CON-07`: Implementar `SeguridadAlertasConsumer.java` escuchando `cola.seguridad.alertas` con ACK manual.
- [x] `CON-08`: Implementar captura de excepciones y desvío a DLQ mediante `channel.basicNack(deliveryTag, false, false)` ante errores permanentes.

### Fase 7: Validación Integral y Pruebas de Desempeño

- [x] `TST-01`: Ejecutar compilación y pruebas completas de ambos microservicios con Maven Wrapper (`./mvnw clean test` en backend y consumer: 40 tests en backend + 9 tests en consumer = 49 tests pasando).
- [ ] `TST-02`: Ejecutar prueba de ciclo de vida CRUD y verificar inserción automática en `pedidos360_db.outbox_eventos`.
- [ ] `TST-03`: Ejecutar prueba de retención: apagar consumidor, emitir 3 pedidos, verificar mensajes en estado Ready en RabbitMQ, encender consumidor y validar vaciado.
- [ ] `TST-04`: Ejecutar prueba de falla y DLQ: enviar payload con error simulado, comprobar desvío a `cola.*.dlq` y presencia de cabecera `x-death`.
- [ ] `TST-05`: Ejecutar prueba de seguridad: emitir mutación con usuario sin grupo Admin y comprobar recepción de alerta en `cola.seguridad.alertas`.
- [x] `TST-06`: Crear archivo `compose.cluster.yaml` y script de verificación de clúster para preparación de EP4 (`infraestructura/compose.cluster.yaml` y `unir-cluster.ps1`).

---

## 9. Pruebas Necesarias y Evidencias Requeridas

| Identificador | Prueba Requerida           | Procedimiento de Ejecución                                                                      | Resultado Esperado / Evidencia                                                                                            |
| :------------ | :------------------------- | :---------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------ |
| **PRU-01**    | **Seguridad RBAC (403)**   | Enviar `POST /api/pedidos` con token de usuario sin grupo `Admin`.                              | Respuesta HTTP `403 Forbidden`. Mensaje enrutado a `cola.seguridad.alertas`.                                              |
| **PRU-02**    | **At-Least-Once Outbox**   | Detener RabbitMQ (`docker stop rabbitmq`). Crear un producto vía `POST`.                        | HTTP `201 Created`. Registro persistido en `pedidos` y evento en `outbox_eventos` con estado `PENDIENTE`.                 |
| **PRU-03**    | **Recuperación Outbox**    | Iniciar RabbitMQ (`docker start rabbitmq`). Esperar ciclo de worker.                            | Evento publicado en RabbitMQ y estado en `outbox_eventos` actualizado a `PUBLICADO`.                                      |
| **PRU-04**    | **Enrutamiento Topic**     | Crear producto (`inventario.producto.creado`) y eliminar producto (`bajas.producto.eliminado`). | Creación llega a `cola.auditoria.inventario`. Eliminación llega a `cola.auditoria.eliminaciones`. Cero cruces erróneos.   |
| **PRU-05**    | **Retención en Cola**      | Detener Consumidor. Emitir 3 mutaciones. Consultar RabbitMQ Management.                         | 3 mensajes en estado `Ready`. Al levantar consumidor, estado vuelve a 0 y mensajes persisten en `auditoria_db`.           |
| **PRU-06**    | **NACK y Envío a DLQ**     | Enviar mensaje con payload corrupto o palabra clave `ERROR`.                                    | Consumidor ejecuta `basicNack(false, false)`. Mensaje aparece en `cola.*.dlq` con metadatos de excepción.                 |
| **PRU-07**    | **Idempotencia**           | Reinyectar manualmente el mismo `evento_id` a la cola.                                          | Consumidor emite `basicAck` sin generar duplicados en la base de datos `auditoria_db`.                                    |
| **PRU-08**    | **API Admin REST**         | Invocar `POST /api/admin/queues` con JSON válido y con JSON sin nombre.                         | `201 Created` con nombre válido; `400 Bad Request` ante validación fallida. Invocar `DELETE` elimina la cola en RabbitMQ. |
| **PRU-09**    | **Clúster RabbitMQ (EP4)** | Levantar dos nodos con `compose.cluster.yaml` y ejecutar `rabbitmqctl cluster_status`.          | Salida mostrando nodos `{rabbit@rabbit1, rabbit@rabbit2}` sincronizados.                                                  |

---

## 10. Problemas Pendientes y Decisiones de Usuario

1. **Gestión del Repositorio para el Microservicio Consumidor:**
   - _Situación:_ La rúbrica exige que el backend corresponda a _"varios microservicios construidos en Java con Spring Boot"_.
   - _Decisión requerida:_ ¿Deseas que el microservicio consumidor (`pedidos360-consumer`) resida como un módulo/subcarpeta dentro de este mismo repositorio Git, o prefieres un repositorio Git separado?
   - _Recomendación técnica:_ Incluirlo como subcarpeta/módulo Maven (`pedidos360-consumer`) dentro de este repositorio para facilitar el versionamiento y la entrega conjunta al docente.
2. **Sincronización Local de API Gateway vs Backend:**
   - _Situación:_ En `pedidos360-front/src/environments/environment.ts`, la URL apunta al API Gateway en AWS (`https://2c0yqndsn0.execute-api...`).
   - _Impacto:_ Durante las pruebas locales, si el API Gateway no tiene un túnel hacia tu máquina local (o si la instancia EC2 está apagada), el frontend no alcanzará el backend en `localhost:8888`.
   - _Solución:_ Usar un perfil de desarrollo local en Angular apuntando a `http://localhost:8888/api/pedidos` para pruebas de taller, y la URL de API Gateway para la demostración EP4 en la nube.

---

## 11. Registro de Cambios Realizados

| Versión |    Fecha    | Autor / Agente | Resumen de Modificaciones                                                                                                                                                                                                                                                                                          |
| :-----: | :---------: | :------------: | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **1.0** | 04-Nov-2026 | Antigravity AI | Creación inicial del **MASTERPLAN.md**. Integración de especificaciones de rúbricas EP3 y EP4, arquitectura PostgreSQL (2 bases lógicas), topología RabbitMQ con 3 colas y 3 DLQs, patrón Transactional Outbox, política de reintentos y ACK manual, RBAC con Cognito y lista maestra de tareas rastreables.       |
| **1.1** | 04-Nov-2026 | Antigravity AI | Incorporación de archivos de infraestructura iniciales: `docker-compose.yml`, `.env.example`, `.env`, y scripts de inicialización de bases de datos `infraestructura/init-databases.sh` e `init-databases.sql`.                                                                                                    |
| **1.2** | 06-Nov-2026 | Antigravity AI | Implementación completa de Fases 1 a 6 y preparación EP4: Backend principal con Outbox, RabbitConfig, Security RBAC Cognito, RabbitAdminController; Microservicio independiente `pedidos360-consumer` con ACK manual, DLQ e idempotencia en `auditoria_db`; scripts DDL y clúster RabbitMQ `compose.cluster.yaml`. |

---

_Este documento constituye la única guía directriz para las fases posteriores de programación y verificación técnica del proyecto._
