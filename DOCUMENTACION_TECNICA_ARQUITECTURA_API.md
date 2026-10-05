# Documentación Técnica de Arquitectura y Especificación de API REST
## Microservicio ETL de Entradas F1 Ticketing (`microservice_ticketing_gp`)

**Proyecto:** Grand Prix Tracker — Actividad Integradora de Arquitectura de Software  
**Autor:** Antigravity Architect & Technical Writer  
**Versión:** 1.0.0  
**Fecha:** Octubre 2026  
**Estándares Aplicados:** Richardson Maturity Model (Nivel 2), OpenAPI Specification 3.1.0, RFC 9457 (Problem Details for HTTP APIs)

---

## 1. Análisis del Contexto y Rol Arquitectónico Dual (CRÍTICO)

### 1.1. Topología del Sistema y Ubicación del Microservicio
El microservicio `microservice_ticketing_gp` opera como un componente crítico de infraestructura e integración en el ecosistema de la plataforma de Grandes Premios de Fórmula 1. Su diseño responde al patrón arquitectónico **Anti-Corruption Layer (ACL)** y **Fachada Adaptadora (Facade/Adapter Pattern)**, cumpliendo un rol bifronte en la topología de servicios:

```
┌────────────────────────────────────────────────────────┐
│                   Backend Core (API)                   │
│         (Node.js / Express / Spring Gateway)           │
└──────────────────────────┬─────────────────────────────┘
                           │ Consumo REST / JSON (RFC 9457)
                           │ HTTP Verbs / Level 2 Richardson
                           ▼
┌────────────────────────────────────────────────────────┐
│         microservice_ticketing_gp (Spring Boot)        │
│                                                        │
│  [API REST Provider]      [ETL & Domain Logic]         │
│  Controllers v1           TicketingSyncService         │
│  Problem Details Mapper   TicketingAdapter             │
│                           TicketingReservationService  │
│                                                        │
│  [JAX-WS SOAP Client]     [Persistencia Supabase]      │
│  CXF / JAX-WS Stubs       Spring Data JPA Repositories │
└─────────────┬──────────────────────────┬───────────────┘
              │                          │
              │ Llamadas SOAP 1.1        │ Upsert Atómico / Select
              │ XML / HTTP POST          │ PostgreSQL (HikariCP)
              ▼                          ▼
┌──────────────────────────┐   ┌─────────────────────────┐
│ f1-ticketing-legacy-soap │   │   Supabase PostgreSQL   │
│  (Sistema Legado WSDL)   │   │  public.entradas_gradas │
└──────────────────────────┘   └─────────────────────────┘
```

### 1.2. El Doble Rol Arquitectónico
1. **Rol Aguas Arriba (Upstream - Proveedor REST):**  
   Hacia el **Backend Core**, el microservicio expone una API REST moderna, predecible, orientada a recursos y tipificada bajo el **Nivel 2 de Madurez de Richardson**. Utiliza identificadores URI canónicos, verbos HTTP normativos (`GET`, `POST`, `DELETE`), encabezados de control de caché y serialización pura en JSON.
2. **Rol Aguas Abajo (Downstream - Consumidor JAX-WS / SOAP):**  
   Hacia el proveedor de ticketing (`f1-ticketing-legacy-soap`), el microservicio actúa como un cliente consumidor estricto de Servicios Web SOAP 1.1 basados en WSDL y XML. A través de stubs generados mediante el plugin Apache CXF (`cxf-codegen-plugin`) e implementados sobre el runtime de JAX-WS (`com.sun.xml.ws:jaxws-rt`), genera sobres SOAP (`soapenv:Envelope`), envía documentos XML estructurados (`consultarDisponibilidadRequest`, `reservarEntradasRequest`) y procesa respuestas fuertemente acopladas.

### 1.3. Pipeline ETL y Persistencia en Supabase
La misión primordial del microservicio es la ingesta y consolidación de inventario mediante un pipeline **Extract-Transform-Load (ETL)**:
* **Extract:** Invoca remotamente la operación `consultarDisponibilidad(codigoEvento)` del SOAP, recibiendo arreglos de `InventarioGrada`.
* **Transform:** El componente `TicketingAdapter` aísla los DTOs SOAP y normaliza los tipos de entrada para dar cumplimiento estricto a la restricción CHECK de PostgreSQL en Supabase (`entradas_gradas_tipo_check`), mapeando categorías heterogéneas (`VIP`, `Premium`, `Standard`, `General`) exclusivamente a `'VIP'`, `'Asiento Numerado'` o `'General'`. Sanea además longitudes de cadena, redondos monetarios en `BigDecimal` y stocks no negativos.
* **Resolve:** Traduce el código de evento de carrera (ej: `F1-2026-MAD`) al identificador foráneo `id_evento` (UUID) en la tabla `public.eventos_f1`.
* **Load (Upsert Idempotente):** Verifica la existencia previa de la tupla `(id_evento, nombre_tribuna)`. Si existe, actualiza el precio y el stock disponible; si no, inserta un nuevo registro con UUID autogenerado. Esto asegura atomicidad e idempotencia matemática frente a ejecuciones concurrentes o reintentos de red.

### 1.4. Rol como Proxy Transaccional de Reservas
En el flujo de confirmación de órdenes de compra, el microservicio asume la responsabilidad de proxy transaccional: recibe la solicitud de reserva desde el Backend Core (`POST /api/v1/tickets/reservas`), localiza la metadata en Supabase en modo solo lectura (`@Transactional(readOnly = true)`) y delega la ejecución de la reserva directamente en el sistema legado mediante `reservarEntradas(...)`. Esto garantiza que la deducción de inventario se produzca en el sistema maestro (SOAP) sin alterar la responsabilidad de compras del core.

### 1.5. Traducción de Fallas mediante RFC 9457 (Problem Details)
Los sistemas legados SOAP comunican anomalías a través de estructuras XML propietarias (`SOAP Fault`, `<soapenv:Fault>`, `<faultcode>`, `<faultstring>`, y elementos `<detail>` con esquemas como `SinStockFault`). Exponer estas estructuras o dejar escapar excepciones del runtime (`WebServiceException`, `SOAPFaultException`, `SocketTimeoutException`) hacia el Backend Core violaría los principios de encapsulamiento y acoplaría la plataforma a tecnologías obsoletas.  
A través de un controlador de excepciones centralizado (`@RestControllerAdvice`), el microservicio intercepta estas fallas en el límite del sistema y las traduce a documentos normalizados bajo la especificación **RFC 9457 (`application/problem+json`)**, garantizando un contrato de error semántico, trazable y desacoplado.

---

## 2. Entregable 1: Catálogo de Endpoints REST (Nivel 2 de Richardson)

### 2.1. Criterios de Diseño REST Nivel 2
El diseño de la API cumple rigurosamente con el Nivel 2 de Madurez de Richardson:
* **Uso de Recursos en la URI:** Las rutas representan sustantivos jerárquicos (`/api/v1/tickets`) que identifican recursos y colecciones en lugar de verbos procedimentales (estilo RPC).
* **Verbos HTTP Semánticos:** Se aprovechan los métodos estándar del protocolo HTTP:
  * `GET`: Operaciones seguras e idempotentes para recuperación de recursos y consultas en vivo.
  * `POST`: Creación de reservas subordinadas o ejecución controlada de procesos de sincronización.
  * `DELETE`: Operación idempotente de depuración y descarte de recursos.
* **Códigos de Estado HTTP Significativos:** En lugar de devolver siempre `200 OK` con un cuerpo que indique error (antipatrón frecuente en SOAP), la API emplea los códigos `200`, `201`, `202`, `204`, `400`, `404`, `409`, `502` y `504` según la semántica universal de HTTP.
* **Versionado en la URI:** Se implementa el prefijo `/api/v1/` para permitir evolución evolutiva del contrato sin romper clientes existentes.

### 2.2. Tabla Resumen del Catálogo de Endpoints

| Método HTTP | URI | Propósito / Descripción Breve | Códigos de Estado Esperados |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/tickets/sync` | Dispara la sincronización ETL masiva de inventario SOAP hacia Supabase para todos los Grandes Premios. | `202 Accepted`<br>`200 OK`<br>`409 Conflict`<br>`502 Bad Gateway`<br>`504 Gateway Timeout` |
| **POST** | `/api/v1/tickets/sync/{codigoEvento}` | Ejecuta el pipeline ETL para un Gran Premio específico a partir de su código oficial (ej: `F1-2026-MAD`). | `200 OK`<br>`202 Accepted`<br>`400 Bad Request`<br>`404 Not Found`<br>`502 Bad Gateway`<br>`504 Gateway Timeout` |
| **GET** | `/api/v1/tickets/{codigoEvento}` | Consulta el inventario y disponibilidad consolidada (cacheada/persistida) en Supabase para un evento. | `200 OK`<br>`400 Bad Request`<br>`404 Not Found`<br>`500 Internal Server Error` |
| **GET** | `/api/v1/tickets/{codigoEvento}/live` | Proxy en tiempo real que consulta la disponibilidad directamente en el sistema legado SOAP sin pasar por caché. | `200 OK`<br>`400 Bad Request`<br>`502 Bad Gateway`<br>`504 Gateway Timeout` |
| **POST** | `/api/v1/tickets/reservas` | Bloquea y confirma una reserva de entradas contra el sistema legado SOAP emitiendo código de confirmación. | `201 Created`<br>`400 Bad Request`<br>`404 Not Found`<br>`409 Conflict`<br>`502 Bad Gateway`<br>`504 Gateway Timeout` |
| **DELETE** | `/api/v1/tickets/{codigoEvento}` | Elimina el inventario local asociado a un evento pasado o cancelado en la base de datos Supabase. | `204 No Content`<br>`400 Bad Request`<br>`404 Not Found`<br>`409 Conflict`<br>`500 Internal Server Error` |

---

### 2.3. Especificación Detallada de cada Endpoint

#### Endpoint 1: Sincronización Masiva de Inventario
* **Método:** `POST`
* **URI:** `/api/v1/tickets/sync`
* **Descripción Funcional:** Dispara la ejecución del proceso batch ETL sobre la totalidad del catálogo oficial de Fórmula 1 (19 Grandes Premios). Para cada evento, se conecta con el servicio SOAP, extrae las gradas, resuelve las referencias en PostgreSQL y ejecuta el Upsert atómico. Soporta ejecución asíncrona devolviendo un identificador de seguimiento de la tarea.
* **Códigos de Respuesta:**
  * `202 Accepted`: El proceso masivo fue aceptado y encolado exitosamente para ejecución en segundo plano. Incluye cabecera o cuerpo con el estado del trabajo.
  * `200 OK`: En modo síncrono, se procesaron todos los eventos y se retorna el resumen consolidado (`totalProcesadas`, `creadas`, `actualizadas`).
  * `409 Conflict`: Ya existe un proceso de sincronización masiva en ejecución activa (protección por candado distribuido).
  * `502 Bad Gateway`: Fallo irrecuperable de comunicación general con el host SOAP del proveedor legado.
  * `504 Gateway Timeout`: La conexión o respuesta del servicio legado excedió el tiempo límite configurado.

#### Endpoint 2: Sincronización a Demanda de un Evento Específico
* **Método:** `POST`
* **URI:** `/api/v1/tickets/sync/{codigoEvento}`
* **Descripción Funcional:** Ejecuta el ciclo ETL exclusivamente para el Gran Premio provisto en la variable de ruta (`codigoEvento`, e.g., `F1-2026-MAD`). Realiza la extracción SOAP, transformación con reglas de categorización CHECK y el Upsert de las tribunas en Supabase.
* **Parámetros de Ruta:**
  * `codigoEvento` (string, obligatorio): Identificador alfanumérico estandarizado del evento (formato regex: `^F1-\d{4}-[A-Z]{3}$`).
* **Códigos de Respuesta:**
  * `200 OK`: Sincronización completada exitosamente; se retorna la metadata del evento y el arreglo de tribunas actualizadas/creadas.
  * `202 Accepted`: Si se parametriza en modo asíncrono diferido.
  * `400 Bad Request`: Formato de código de evento no válido o ausente.
  * `404 Not Found`: El código de evento no corresponde a ningún Gran Premio conocido ni en catálogo ni en Supabase.
  * `502 Bad Gateway`: El servicio SOAP respondió con un SOAP Fault de servidor o XML corrupto para dicho evento.
  * `504 Gateway Timeout`: La operación `consultarDisponibilidad` del SOAP no respondió dentro de los 5 segundos de tolerancia.

#### Endpoint 3: Consulta de Disponibilidad Local (Persistida en Supabase)
* **Método:** `GET`
* **URI:** `/api/v1/tickets/{codigoEvento}`
* **Descripción Funcional:** Recupera el inventario de entradas de tribunas almacenado en la tabla `public.entradas_gradas` de Supabase para un evento de F1. Proporciona una respuesta de ultra-baja latencia consumida por la interfaz de usuario y el Backend Core sin ejercer carga alguna sobre el frágil sistema SOAP legado.
* **Parámetros de Ruta:**
  * `codigoEvento` (string, obligatorio): Código del evento a consultar (ej: `F1-2026-MAD`).
* **Códigos de Respuesta:**
  * `200 OK`: Colección de gradas recuperada exitosamente con sus IDs únicos, tribunas, tipo normalizado, precio y stock disponible local.
  * `400 Bad Request`: Parámetro de ruta sintácticamente incorrecto.
  * `404 Not Found`: No existe inventario local ni registro del evento solicitado en Supabase.
  * `500 Internal Server Error`: Falla de conectividad o transacción con el pool de conexiones de Supabase PostgreSQL.

#### Endpoint 4: Proxy de Disponibilidad en Vivo (Bypass SOAP)
* **Método:** `GET`
* **URI:** `/api/v1/tickets/{codigoEvento}/live`
* **Descripción Funcional:** Ejecuta una consulta directa y en tiempo real contra la operación SOAP `consultarDisponibilidad`, traduciendo al vuelo el XML recibido a un JSON canónico sin impactar ni persistir en la base de datos de Supabase. Empleado por el Backend Core en instancias críticas inmediatamente previas al pago para verificar stock de última hora.
* **Parámetros de Ruta:**
  * `codigoEvento` (string, obligatorio): Código oficial del Gran Premio.
* **Códigos de Respuesta:**
  * `200 OK`: Disponibilidad en vivo recuperada exitosamente desde el SOAP y traducida a JSON.
  * `400 Bad Request`: Código de evento no válido.
  * `502 Bad Gateway`: El proveedor SOAP retornó un error de procesamiento o no devolvió un sobre válido.
  * `504 Gateway Timeout`: El sistema SOAP tardó más de 5 segundos en generar la respuesta XML.

#### Endpoint 5: Bloqueo / Reserva de Entradas en el Sistema Legado
* **Método:** `POST`
* **URI:** `/api/v1/tickets/reservas`
* **Descripción Funcional:** Actúa como proxy transaccional hacia la operación SOAP `reservarEntradas(codigoEvento, tribuna, cantidad)`. Descuenta el stock en el sistema legado y emite un código oficial de confirmación (ej: `TKT-99812`). Si el stock disponible en el sistema legado es inferior al solicitado, traduce el `SinStockFault` a un estado de conflicto HTTP.
* **Cuerpo de la Petición (`application/json`):**
  ```json
  {
    "codigoEvento": "F1-2026-MAD",
    "nombreTribuna": "Paddock Club Madrid",
    "cantidad": 2
  }
  ```
* **Códigos de Respuesta:**
  * `201 Created`: Reserva completada con éxito en el sistema legado. Devuelve código de confirmación, montos y sellos de tiempo.
  * `400 Bad Request`: Datos de solicitud inválidos (ej. cantidad menor o igual a cero, campos en blanco).
  * `404 Not Found`: El evento o la tribuna especificada no existen en el catálogo legado.
  * `409 Conflict`: Conflicto de inventario. El sistema SOAP devolvió un `SinStockFault` indicando que no hay asientos remanentes suficientes.
  * `502 Bad Gateway`: Fallo inesperado en el nodo SOAP externo al procesar la reserva.
  * `504 Gateway Timeout`: Timeout en la comunicación SOAP al intentar registrar la reserva.

#### Endpoint 6: Depuración de Catálogo Local para Eventos Pasados
* **Método:** `DELETE`
* **URI:** `/api/v1/tickets/{codigoEvento}`
* **Descripción Funcional:** Elimina físicamente las entradas asociadas a un evento de Fórmula 1 en la base de datos local `public.entradas_gradas` de Supabase. Se utiliza en tareas de mantenimiento para purgar Grandes Premios finalizados o cancelados, manteniendo los índices optimizados.
* **Parámetros de Ruta:**
  * `codigoEvento` (string, obligatorio): Código del evento cuyo inventario se eliminará.
* **Códigos de Respuesta:**
  * `204 No Content`: El inventario del evento fue eliminado exitosamente; no hay cuerpo en la respuesta.
  * `400 Bad Request`: Código de evento no reconocido o inválido.
  * `404 Not Found`: El evento indicado no registra entradas almacenadas en Supabase.
  * `409 Conflict`: No es posible eliminar el inventario debido a restricciones de integridad referencial activas.
  * `500 Internal Server Error`: Error interno al procesar el borrado en la base de datos.

---

## 3. Entregable 2: Contrato OpenAPI 3.1.0 (YAML)

A continuación se presenta el fragmento oficial del contrato OpenAPI en su versión **3.1.0**, describiendo con absoluta precisión los dos endpoints requeridos:
1. `POST /api/v1/tickets/sync/{codigoEvento}` (Sincronización específica).
2. `GET /api/v1/tickets/{codigoEvento}` (Consulta local de disponibilidad).

Incluye esquemas exhaustivos de petición, respuestas exitosas y la definición canónica del estándar **RFC 9457 (Problem Details)** bajo el tipo MIME `application/problem+json`.

```yaml
openapi: 3.1.0
info:
  title: API Microservicio F1 Ticketing GP
  version: 1.0.0
  description: >-
    Especificación de la API REST del microservicio microservice_ticketing_gp.
    Cumple con el Nivel 2 de Madurez de Richardson y expone operaciones de sincronización
    ETL (SOAP a PostgreSQL) y consulta de disponibilidad de entradas para Grandes Premios.
  contact:
    name: Equipo de Arquitectura de Software GP
    email: arquitectura@f1gp.uade.edu.ar
servers:
  - url: https://api.f1gp.uade.edu.ar
    description: Servidor de Producción / Staging
  - url: http://localhost:8083
    description: Entorno de Desarrollo Local

paths:
  /api/v1/tickets/sync/{codigoEvento}:
    post:
      summary: Disparar sincronización ETL para un evento específico
      description: >-
        Inicia la extracción de disponibilidad desde el servicio legado SOAP (f1-ticketing-legacy-soap),
        transforma y normaliza los tipos de tribuna según la restricción CHECK de PostgreSQL, y
        ejecuta una operación atómica de Upsert sobre la tabla 'entradas_gradas' en Supabase.
      operationId: syncTicketsByEvent
      tags:
        - Sincronización ETL
      parameters:
        - name: codigoEvento
          in: path
          required: true
          description: Código identificador oficial del Gran Premio legado (ej. F1-2026-MAD).
          schema:
            type: string
            pattern: '^F1-\d{4}-[A-Z]{3}$'
            example: "F1-2026-MAD"
      requestBody:
        description: Parámetros opcionales de control para la sincronización.
        required: false
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SyncEventOptionsRequest'
      responses:
        '200':
          description: Sincronización completada con éxito. Retorna el resumen del procesamiento y las entradas actualizadas.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SyncSuccessResponse'
        '202':
          description: La solicitud de sincronización fue aceptada para procesamiento en segundo plano.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SyncAcceptedResponse'
        '400':
          description: Código de evento inválido o parámetros de solicitud malformados.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '404':
          description: El evento especificado no fue localizado en el catálogo ni en Supabase.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '502':
          description: Fallo de comunicación con el servicio legado SOAP (SOAP Fault o respuesta XML inválida).
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '504':
          description: El servicio legado SOAP superó el tiempo máximo de espera (Timeout de red > 5s).
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'

  /api/v1/tickets/{codigoEvento}:
    get:
      summary: Consultar disponibilidad local consolidada de un evento
      description: >-
        Retorna la disponibilidad actual y los precios de las tribunas persistidas en Supabase
        (public.entradas_gradas) para el evento especificado. No contacta al servicio legado SOAP.
      operationId: getLocalTicketsByEvent
      tags:
        - Catálogo y Disponibilidad
      parameters:
        - name: codigoEvento
          in: path
          required: true
          description: Código identificador del Gran Premio a consultar.
          schema:
            type: string
            pattern: '^F1-\d{4}-[A-Z]{3}$'
            example: "F1-2026-MAD"
      responses:
        '200':
          description: Catálogo de entradas recuperado exitosamente desde la base de datos local.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EventAvailabilityResponse'
        '400':
          description: Código de evento inválido.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '404':
          description: No se encontró disponibilidad ni entradas para el evento indicado.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '500':
          description: Error interno al acceder a la base de datos Supabase.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'

components:
  schemas:
    # Esquema Opciones de Sincronización
    SyncEventOptionsRequest:
      type: object
      properties:
        forceRefresh:
          type: boolean
          description: Indica si se debe omitir cualquier caché en memoria y consultar obligatoriamente al SOAP.
          default: false
        dryRun:
          type: boolean
          description: Si es true, simula la extracción y transformación sin persistir en Supabase.
          default: false

    # Esquema Respuesta 200 OK de Sincronización
    SyncSuccessResponse:
      type: object
      required:
        - success
        - message
        - data
      properties:
        success:
          type: boolean
          example: true
        message:
          type: string
          example: "Sincronización ETL de entradas para F1-2026-MAD (Madrid) completada con éxito."
        data:
          $ref: '#/components/schemas/TicketingSyncResultData'

    # Esquema Respuesta 202 Accepted
    SyncAcceptedResponse:
      type: object
      required:
        - success
        - message
        - jobId
        - status
      properties:
        success:
          type: boolean
          example: true
        message:
          type: string
          example: "Proceso de sincronización iniciado en segundo plano para el evento F1-2026-MAD."
        jobId:
          type: string
          format: uuid
          example: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        status:
          type: string
          enum: [PENDING, PROCESSING]
          example: "PROCESSING"
        timestamp:
          type: string
          format: date-time
          example: "2026-10-05T15:10:00Z"

    # Estructura del Resultado ETL de Sincronización
    TicketingSyncResultData:
      type: object
      required:
        - codigoEvento
        - idEvento
        - carrera
        - temporada
        - totalEntradasExtraidas
        - entradasCreadas
        - entradasActualizadas
        - estado
        - entradas
      properties:
        codigoEvento:
          type: string
          example: "F1-2026-MAD"
        idEvento:
          type: string
          format: uuid
          example: "992ae124-3d59-4adb-9fd2-f0e825c605e8"
        carrera:
          type: string
          example: "Madrid"
        temporada:
          type: integer
          example: 2026
        fechaCarrera:
          type: string
          format: date
          example: "2026-09-11"
        totalEntradasExtraidas:
          type: integer
          example: 4
        entradasCreadas:
          type: integer
          example: 0
        entradasActualizadas:
          type: integer
          example: 4
        estado:
          type: string
          enum: [SUCCESS, WARNING, ERROR]
          example: "SUCCESS"
        mensaje:
          type: string
          example: "ETL completado satisfactoriamente."
        fechaSincronizacion:
          type: string
          format: date-time
          example: "2026-10-05T15:10:02Z"
        entradas:
          type: array
          items:
            $ref: '#/components/schemas/EntradaSummary'

    # Esquema de Entrada Individual
    EntradaSummary:
      type: object
      required:
        - idEntrada
        - idEvento
        - nombreTribuna
        - precioUsd
        - stockDisponible
        - tipo
      properties:
        idEntrada:
          type: string
          format: uuid
          example: "55555555-0000-4000-8000-000000000003"
        idEvento:
          type: string
          format: uuid
          example: "992ae124-3d59-4adb-9fd2-f0e825c605e8"
        nombreTribuna:
          type: string
          maxLength: 100
          example: "Paddock Club Madrid"
        precioUsd:
          type: number
          format: double
          example: 3500.00
        stockDisponible:
          type: integer
          minimum: 0
          example: 500
        tipo:
          type: string
          enum:
            - "VIP"
            - "Asiento Numerado"
            - "General"
          example: "VIP"

    # Esquema Respuesta Disponibilidad Local
    EventAvailabilityResponse:
      type: object
      required:
        - success
        - codigoEvento
        - idEvento
        - carrera
        - totalTribunas
        - gradas
      properties:
        success:
          type: boolean
          example: true
        codigoEvento:
          type: string
          example: "F1-2026-MAD"
        idEvento:
          type: string
          format: uuid
          example: "992ae124-3d59-4adb-9fd2-f0e825c605e8"
        carrera:
          type: string
          example: "Madrid"
        temporada:
          type: integer
          example: 2026
        totalTribunas:
          type: integer
          example: 4
        ultimaSincronizacion:
          type: string
          format: date-time
          example: "2026-10-05T03:00:00Z"
        gradas:
          type: array
          items:
            $ref: '#/components/schemas/EntradaSummary'

    # Esquema Estándar RFC 9457 (Problem Details for HTTP APIs)
    ProblemDetails:
      type: object
      required:
        - type
        - title
        - status
        - detail
        - instance
      properties:
        type:
          type: string
          format: uri
          description: Referencia URI que categoriza formalmente el tipo de error.
          example: "https://api.f1gp.uade.edu.ar/errors/bad-gateway"
        title:
          type: string
          description: Resumen breve y legible en lenguaje humano sobre el tipo de problema.
          example: "Error en Pasarela Aguas Abajo (SOAP Legacy Fault)"
        status:
          type: integer
          description: Código de estado HTTP asignado por el servidor de origen.
          example: 502
        detail:
          type: string
          description: Explicación detallada y contextualizada de la ocurrencia específica del problema.
          example: "El servicio legado de ticketing F1 devolvió un SOAP Fault al consultar disponibilidad para 'F1-2026-MAD'."
        instance:
          type: string
          format: uri-reference
          description: Identificador URI del recurso específico donde se produjo la anomalía.
          example: "/api/v1/tickets/sync/F1-2026-MAD"
        timestamp:
          type: string
          format: date-time
          description: Marca de tiempo ISO-8601 en la que se interceptó el problema.
          example: "2026-10-05T15:10:05.123Z"
        traceId:
          type: string
          description: Identificador de correlación unívoco para trazabilidad distribuida.
          example: "req-f1-8a9d1c24-5b7e"
        serviceSource:
          type: string
          description: Subsistema o servicio externo aguas abajo causante del incidente.
          example: "f1-ticketing-legacy-soap"
        errorCode:
          type: string
          description: Código interno de error funcional o técnico.
          example: "LEGACY_SOAP_FAULT"
```

---

## 4. Entregable 3: Ejemplos de Cargas Útiles JSON

### 4.1. Ejemplo 1: Respuesta Exitosa de Sincronización (`202 Accepted` / `200 OK`)

Este ejemplo muestra la carga útil devuelta por el microservicio al invocar el endpoint `POST /api/v1/tickets/sync/F1-2026-MAD`.  
Representa el resultado consolidado del pipeline ETL: las 4 gradas del Gran Premio de Madrid fueron recuperadas del servicio SOAP, transformadas respetando la restricción CHECK de PostgreSQL (`VIP`, `Asiento Numerado`, `General`) y actualizadas atómicamente en Supabase.

#### Encabezados HTTP:
```http
HTTP/1.1 202 Accepted
Content-Type: application/json; charset=UTF-8
Location: /api/v1/tickets/sync/jobs/7e29b14d-95cf-4df5-a7b1-2d7c5f8841e9
Date: Mon, 05 Oct 2026 15:10:00 GMT
```

#### Cuerpo JSON:
```json
{
  "success": true,
  "message": "Sincronización ETL de entradas para F1-2026-MAD (Madrid) completada con éxito. Total procesadas: 4 (Creadas: 0, Actualizadas: 4)",
  "jobId": "7e29b14d-95cf-4df5-a7b1-2d7c5f8841e9",
  "status": "COMPLETED",
  "data": {
    "codigoEvento": "F1-2026-MAD",
    "idEvento": "992ae124-3d59-4adb-9fd2-f0e825c605e8",
    "carrera": "Madrid",
    "temporada": 2026,
    "fechaCarrera": "2026-09-11",
    "totalEntradasExtraidas": 4,
    "entradasCreadas": 0,
    "entradasActualizadas": 4,
    "estado": "SUCCESS",
    "mensaje": "Inventario de tribunas y stock sincronizado satisfactoriamente en Supabase PostgreSQL.",
    "fechaSincronizacion": "2026-10-05T15:10:01.428Z",
    "entradas": [
      {
        "idEntrada": "55555555-0000-4000-8000-000000000003",
        "idEvento": "992ae124-3d59-4adb-9fd2-f0e825c605e8",
        "nombreTribuna": "Paddock Club Madrid",
        "precioUsd": 3500.00,
        "stockDisponible": 500,
        "tipo": "VIP"
      },
      {
        "idEntrada": "55555555-0000-4000-8000-000000000002",
        "idEvento": "992ae124-3d59-4adb-9fd2-f0e825c605e8",
        "nombreTribuna": "Tribuna Principal",
        "precioUsd": 1200.00,
        "stockDisponible": 2500,
        "tipo": "Asiento Numerado"
      },
      {
        "idEntrada": "55555555-0000-4000-8000-000000000001",
        "idEvento": "992ae124-3d59-4adb-9fd2-f0e825c605e8",
        "nombreTribuna": "Grada Curva 4",
        "precioUsd": 550.00,
        "stockDisponible": 5000,
        "tipo": "Asiento Numerado"
      },
      {
        "idEntrada": "55555555-0000-4000-8000-000000000000",
        "idEvento": "992ae124-3d59-4adb-9fd2-f0e825c605e8",
        "nombreTribuna": "Pelouse General",
        "precioUsd": 200.00,
        "stockDisponible": 12000,
        "tipo": "General"
      }
    ]
  }
}
```

---

### 4.2. Ejemplo 2: Respuesta de Error Upstream (`502 Bad Gateway` con RFC 9457)

Este escenario simula el momento en el que el microservicio intenta invocar la operación `consultarDisponibilidad` contra `f1-ticketing-legacy-soap`, pero el servidor legado colapsa y devuelve un fallo XML nativo (`SOAP Fault`).

#### Simulación del SOAP Fault Recibido (Capa Interna Oculta):
```xml
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Header/>
  <SOAP-ENV:Body>
    <SOAP-ENV:Fault>
      <faultcode>SOAP-ENV:Server</faultcode>
      <faultstring>Database connection pool exhausted in legacy host 10.0.4.12: Sybase ASE SQL error 1105</faultstring>
      <detail>
        <legacyErrorTrace>com.sybase.jdbc4.jdbc.SybSQLException: Could not allocate space for object in database 'f1_ticketing'</legacyErrorTrace>
      </detail>
    </SOAP-ENV:Fault>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

#### Transformación del `ExceptionMapper` / `@RestControllerAdvice`:
El handler captura la excepción `jakarta.xml.ws.soap.SOAPFaultException`, purga los volcados internos de stack traces de Sybase/Java y las etiquetas XML/JAX-WS, y emite una respuesta sanitizada, consistente y tipada bajo la especificación **RFC 9457**.

#### Encabezados HTTP:
```http
HTTP/1.1 502 Bad Gateway
Content-Type: application/problem+json; charset=UTF-8
Date: Mon, 05 Oct 2026 15:10:05 GMT
```

#### Cuerpo JSON (RFC 9457 Problem Details):
```json
{
  "type": "https://api.f1gp.uade.edu.ar/errors/bad-gateway",
  "title": "Error en Pasarela Aguas Abajo (SOAP Legacy Fault)",
  "status": 502,
  "detail": "El servicio legado de ticketing F1 (SOAP) reportó un error interno de servidor al procesar la consulta de disponibilidad para el evento 'F1-2026-MAD'. La persistencia en Supabase no fue alterada.",
  "instance": "/api/v1/tickets/sync/F1-2026-MAD",
  "timestamp": "2026-10-05T15:10:05.123Z",
  "traceId": "req-f1-8a9d1c24-5b7e41fa-8910",
  "serviceSource": "f1-ticketing-legacy-soap",
  "errorCode": "LEGACY_SOAP_FAULT",
  "suggestedAction": "Verifique el estado del contenedor SOAP legado o reintente la operación utilizando backoff exponencial."
}
```

---

## 5. Entregable 4: Decisiones Justificadas y Manejo de Fallas (El Puente SOAP-REST)

### 5.1. Justificación de Idempotencia y Sincronización Concurrente
Para garantizar que múltiples peticiones simultáneas al endpoint `POST /api/v1/tickets/sync` —ocasionadas, por ejemplo, por desajustes en el disparador del cron job diario, múltiples réplicas en clúster o ráfagas de reintentos automáticos del Backend Core— no saturen ni degraden al frágil sistema SOAP legado, nuestra arquitectura combina un mecanismo de **candado distribuido (Distributed Lock)** en la capa de aplicación con una estrategia de **Upsert estrictamente idempotente** en la base de datos PostgreSQL de Supabase. A nivel de orquestación, cada solicitud entrante intenta adquirir un bloqueo atómico con tiempo de expiración (TTL) basado en un identificador unívoco de tarea (`sync:tickets:lock`); si una sincronización masiva ya se encuentra en curso, cualquier invocación redundante es descartada inmediatamente retornando un código HTTP `409 Conflict` (o canalizada sin duplicar ejecuciones I/O), neutralizando el riesgo de tormentas de peticiones (*thundering herd problem*). Complementariamente, en la fase de carga (Load), la persistencia evalúa la clave natural compuesta `(id_evento, nombre_tribuna)` mediante `findByIdEventoAndNombreTribunaIgnoreCase`, aplicando una mutación in-place de stock y precio para entidades preexistentes o inserción atómica para nuevas entradas. Esta convergencia de estado garantiza consistencia eventual, elimina la posibilidad de registros duplicados y blinda la capacidad de cómputo del servidor SOAP frente a sobrecargas concurrentes.

### 5.2. Mecanismo de Timeout en JAX-WS y Aislamiento de Fallas (504 Gateway Timeout)
En la integración síncrona con el sistema legado, el cliente JAX-WS (`F1TicketingSoapPort`) está blindado mediante la parametrización imperativa de timeouts a nivel de transporte HTTP sobre el `BindingProvider`: se definen `jakarta.xml.ws.client.connectionTimeout` (establecimiento de enlace TCP) y `jakarta.xml.ws.client.receiveTimeout` (lectura del paquete SOAP/XML) en un umbral estricto de **5000 milisegundos**. Si el sistema legado experimenta congestión de hilos, cortes de conectividad o demora más de 5 segundos en emitir el XML de respuesta, el socket subyacente aborta la conexión lanzando una excepción `java.net.SocketTimeoutException`, la cual es encapsulada por el runtime en una `jakarta.xml.ws.WebServiceException`. En lugar de permitir que esta falla desborde en un error genérico `500 Internal Server Error` o bloquee los hilos de ejecución del contenedor Servlet agotando el pool de conexiones, nuestro `@RestControllerAdvice` (`GlobalExceptionHandler`) intercepta selectivamente la causa raíz de la excepción, distingue la interrupción temporal de red de un error de aplicación y responde al Backend Core con un código HTTP **`504 Gateway Timeout`** formateado conforme a **RFC 9457 (`application/problem+json`)**. De este modo, el Backend Core recibe un fallo predecible en tiempo acotado, liberando recursos de memoria y evitando bloqueos indefinidos en cascada a lo largo de toda la cadena de microservicios.

---

## 6. Conclusión y Resumen de Buenas Prácticas Arquitectónicas

1. **Aislamiento Total de Tecnologías:** El Backend Core opera exclusivamente con primitivas REST modernas, JSON y HTTP canónico, ignorando por completo la existencia de sobres XML, puertos JAX-WS, WSDL y tipados complejos de Java/SOAP.
2. **Defensa en Profundidad de Base de Datos:** La transformación en `TicketingAdapter` garantiza que ningún dato anómalo de sistemas externos viole las restricciones `CHECK` de integridad relacional en Supabase (`'VIP'`, `'Asiento Numerado'`, `'General'`).
3. **Resiliencia Operativa y Observabilidad:** Con el soporte de RFC 9457 y trazabilidad por `traceId`, los equipos de soporte y desarrollo pueden correlacionar cualquier degradación en el sistema SOAP legado en milisegundos, salvaguardando la experiencia del usuario final en la plataforma Grand Prix Tracker.

