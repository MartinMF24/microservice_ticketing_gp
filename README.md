# Microservicio ETL de Entradas F1 Ticketing (`microservice_ticketing_gp`)

Microservicio Spring Boot de ingesta y sincronización (ETL) para el catálogo de entradas de tribunas y stock de Grandes Premios de Fórmula 1. Su objetivo es actuar como puente automatizado entre un sistema legado SOAP (`f1-ticketing-legacy-soap`) y la base de datos relacional PostgreSQL (Supabase), asegurando inventario actualizado, normalizado e idempotente sin implementar lógica de compras o reservas.

---

## 1. La Idea y Propósito del Proyecto

En el ecosistema de la plataforma de Grandes Premios, la venta y visualización de entradas en tiempo real requiere datos consolidados en PostgreSQL. Sin embargo, los sistemas proveedores de circuitos o federaciones operan con servicios SOAP legados (JAX-WS / XML).

Este microservicio resuelve este problema mediante un **Pipeline ETL (Extract, Transform, Load)** desacoplado:
1. **Consulta los servicios SOAP** de ticketing para cada Gran Premio.
2. **Transforma y normaliza** los datos heterogéneos del XML a las restricciones estrictas de la base de datos relacional.
3. **Resuelve relaciones foráneas** vinculando cada tribuna con su evento correspondiente en Supabase (`eventos_f1`).
4. **Aplica persistencia Upsert atómica**, garantizando que el stock y los precios se actualicen sin duplicar registros ni romper integridad referencial.
5. **Mantiene el inventario al día** mediante ejecuciones automáticas diarias o disparos a demanda vía REST.

```
┌──────────────────────────────┐
│  Sistema Legado SOAP F1      │
│  (f1-ticketing-legacy-soap)  │
└──────────────┬───────────────┘
               │  1. consultarDisponibilidad(codigoEvento) [SOAP / XML]
               ▼
┌──────────────────────────────────────────────────────────────────┐
│  microservice_ticketing_gp (Spring Boot 3 / Java 21)             │
│                                                                  │
│  [EXTRACT]   TicketingClientService (JAX-WS / CXF Stubs)         │
│                     │                                            │
│  [TRANSFORM] TicketingAdapter (Mapeo categorías & CHECK rule)    │
│                     │                                            │
│  [RESOLVE]   EventoF1Repository (Búsqueda por año/ciudad/nombre) │
│                     │                                            │
│  [LOAD]      TicketingSyncService (Estrategia Upsert idempotente)│
└──────────────┬───────────────────────────────────────────────────┘
               │  2. Inserción / Actualización atómica [JDBC / JPA]
               ▼
┌──────────────────────────────┐
│  Base de Datos PostgreSQL    │
│  (Supabase: entradas_gradas) │
└──────────────────────────────┘
```

---

## 2. ¿Cómo Funciona el Proceso ETL?

El flujo de procesamiento se divide en tres etapas claramente definidas:

### 2.1. Extract (Extracción) — `TicketingClientService`
* A partir de un código de evento (ej. `F1-2026-MAD`), el cliente SOAP JAX-WS compila y envía una petición `consultarDisponibilidad`.
* Recibe un sobre SOAP con una lista de tribunas (`InventarioGrada`) que incluye: nombre de la tribuna, precio base, stock y categoría legacy (`VIP`, `Premium`, `Standard`, `General`).
* Dispone de mecanismos de contingencia y fallback configurable en caso de indisponibilidad temporal del servicio SOAP.

### 2.2. Transform (Transformación) — `TicketingAdapter`
* Convierte las clases generadas por JAX-WS a entidades de dominio JPA `EntradaGrada`.
* **Regla de Negocio Crítica (Restricción CHECK de Base de Datos)**:
  La tabla `entradas_gradas` posee la restricción `entradas_gradas_tipo_check` que **únicamente** acepta los valores:
  `'VIP'`, `'Asiento Numerado'` y `'General'`.
  
  El Adapter normaliza las categorías legadas según la siguiente matriz de equivalencia:
  
  | Categoría SOAP Legada | Tipo en Base de Datos (`tipo`) |
  |---|---|
  | `VIP` | `VIP` |
  | `Premium` | `Asiento Numerado` |
  | `Standard` | `Asiento Numerado` |
  | `General` | `General` |
  | *(Cualquier otra)* | `General` *(valor por defecto seguro)* |

* **Saneamiento de Datos**: Truncado y limpieza de espacios en `nombre_tribuna` (máximo 100 caracteres), redondeo a dos decimales con `BigDecimal` y conversión de stock negativo a `0`.

### 2.3. Resolve (Resolución de Llaves Foráneas)
* Las entradas no pueden existir sin estar vinculadas a un evento en `public.eventos_f1`.
* El servicio analiza el código del evento (ej. año `2026` y plaza `MAD` / `Madrid`) y consulta `EventoF1Repository` para recuperar el `id_evento` (UUID) correspondiente en Supabase, aplicando tolerancia a nombres fonéticos o sin tildes (ej. *Bakú* vs *Baku*).

### 2.4. Load (Carga / Upsert Idempotente) — `TicketingSyncService`
* Para cada tribuna transformada, verifica si ya existe un registro con el par `(id_evento, nombre_tribuna)`:
  * **Si ya existe**: Actualiza `precio_usd`, `stock_disponible` y `tipo`.
  * **Si no existe**: Crea una nueva entidad con UUID generado automáticamente y asigna la relación foránea.
* El proceso es **100% idempotente**: puede ejecutarse múltiples veces consecutivas sin generar registros duplicados ni violaciones de claves primarias.

---

## 3. Patrones de Diseño y Decisiones de Arquitectura

El microservicio aplica patrones de diseño consolidados de la ingeniería de software empresarial:

### 3.1. Patrón Adapter (Adaptador)
* **Clase**: `TicketingAdapter`
* **Propósito**: Desacoplar el modelo de datos generado por el WSDL legado del modelo de dominio JPA del sistema. Si el servicio SOAP modifica el esquema de sus DTOs o agrega atributos, los cambios se aíslan exclusivamente dentro del adaptador, sin impactar la lógica de negocio ni la base de datos.
* **Garantía**: Asegura que ninguna entidad JPA se persista con valores que violen las restricciones CHECK de PostgreSQL.

### 3.2. Arquitectura "Package by Feature"
* El código está organizado por responsabilidades funcionales (`adapter`, `config`, `controller`, `dto`, `model`, `repository`, `service`, `shared`), manteniendo coherencia con los demás microservicios del ecosistema (`microservice_booking_Hotel_gp` y `microservice_flights_gp`).

### 3.3. Pipeline ETL (Extract - Transform - Load)
* Desacoplamiento estricto de las 3 fases del procesamiento de datos:
  * `TicketingClientService` se ocupa exclusivamente de la comunicación I/O con el protocolo SOAP.
  * `TicketingAdapter` se ocupa exclusivamente de la transformación y mapeo en memoria.
  * `TicketingSyncService` se ocupa de la orquestación, resolución de entidades y persistencia transaccional.

### 3.4. Patrón Repository (Spring Data JPA)
* Abstracción del acceso a datos mediante interfaces `EntradaGradaRepository` y `EventoF1Repository`.
* Implementa consultas derivadas optimizadas (`findByIdEventoAndNombreTribunaIgnoreCase`) para verificar existencia de registros antes de persistir.

### 3.5. Patrón Idempotencia / Upsert (Update or Insert)
* Asegura que el estado final de la base de datos sea idéntico independientemente de cuántas veces se invoque el proceso de sincronización. Las entradas ya existentes reflejan el stock más reciente y las nuevas se incorporan sin colisiones.

### 3.6. Resiliencia y Fallback de Compilación
* **CXF Codegen Plugin**: Genera los stubs JAX-WS automáticamente a partir de `TICKETING_WSDL_URL`.
* Si el servicio SOAP externo no está disponible al momento de compilar el proyecto Maven, el build utiliza automáticamente el contrato empaquetado localmente en `src/main/resources/wsdl/ticketing.wsdl`, asegurando compilaciones e integraciones continuas reproducibles.

### 3.7. Manejo Centralizado de Excepciones
* Mediante `@RestControllerAdvice` en `GlobalExceptionHandler` y el envoltorio estándar `ApiResponse<T>`, garantizando que todas las respuestas de la API (éxito o error) posean una estructura JSON uniforme.

---

## 4. Estructura del Código

```text
com.uade.microservices.ticketing/
├── adapter/
│   └── TicketingAdapter.java            # Patrón Adapter: WSDL DTO -> EntradaGrada (regla CHECK)
├── config/
│   ├── CorsConfig.java                  # Orígenes cruzados y cabeceras
│   ├── TicketingSoapClientConfig.java   # Configuración del bean JAX-WS Port
│   └── TicketingSoapProperties.java     # Propiedades de conexión SOAP (URL, timeouts)
├── controller/
│   ├── TicketingReservationController.java # Endpoints de reserva (POST /tickets/reservar)
│   └── TicketingSyncController.java        # Endpoints ETL (POST /all, POST /{codigoEvento})
├── dto/
│   ├── request/
│   │   └── ReservaEntradaRequestDto.java   # Solicitud de reserva (idEntrada, cantidad)
│   └── response/
│       ├── EntradaSyncSummaryDto.java      # Detalle de cada entrada procesada en ETL
│       ├── ReservaEntradaResponseDto.java  # Confirmación de reserva SOAP
│       ├── TicketingSyncAllSummaryDto.java # Resumen general del catálogo
│       └── TicketingSyncResultDto.java     # Detalle del evento y sus entradas
├── model/
│   ├── Circuito.java                       # Entidad JPA public.circuitos
│   ├── Ciudad.java                         # Entidad JPA public.ciudades
│   ├── EntradaGrada.java                   # Entidad JPA principal: public.entradas_gradas
│   ├── EventoF1.java                       # Entidad JPA public.eventos_f1
│   └── GranPremioTarget.java               # Catálogo maestro en memoria de los 19 Grandes Premios
├── repository/
│   ├── CircuitoRepository.java
│   ├── CiudadRepository.java
│   ├── EntradaGradaRepository.java         # Operaciones sobre entradas_gradas (Upsert / Find)
│   └── EventoF1Repository.java             # Búsqueda de eventos por año y circuito/ciudad
├── scheduler/
│   └── TicketingSyncScheduler.java         # Ejecución programada con @Scheduled
├── service/
│   ├── TicketingClientService.java         # EXTRACT & SOAP Client (consultar y reservar)
│   ├── TicketingReservationService.java    # Orquestador de reservas (Read-only Supabase -> SOAP)
│   └── TicketingSyncService.java           # LOAD / ORQUESTADOR ETL: Sincronización masiva
└── shared/
    ├── exception/
    │   ├── GlobalExceptionHandler.java     # Control centralizado de errores HTTP
    │   ├── ResourceNotFoundException.java  # Error 404 (Entrada o Evento inexistente)
    │   └── StockInsuficienteException.java # Error 409 (Rechazo por stock en SOAP)
    └── response/
        └── ApiResponse.java                # Envoltorio estándar de respuesta JSON
```

---

## 5. Catálogo de Endpoints y Guía de Invocación

Los endpoints pueden consumirse tanto en un entorno local como sobre la instancia desplegada en Render.

| Entorno | URL Base (`BASE_URL`) |
|---|---|
| **Local** | `http://localhost:8083` |
| **Render** | `https://<tu-servicio>.onrender.com` |

---

### 5.1. Sincronización Masiva del Catálogo Completo

Ejecuta el pipeline ETL para los **19 Grandes Premios** del calendario oficial de Fórmula 1.

* **Método**: `POST`
* **Ruta**: `/api/microservicios/sync-tickets/all`

#### Invocación Local:
```bash
curl -X POST http://localhost:8083/api/microservicios/sync-tickets/all \
  -H "Content-Type: application/json"
```

#### Invocación en Render:
```bash
curl -X POST https://<tu-servicio>.onrender.com/api/microservicios/sync-tickets/all \
  -H "Content-Type: application/json"
```

#### Respuesta de Ejemplo (`200 OK`):
```json
{
  "success": true,
  "message": "Sincronización masiva de inventario completada. Éxito: 19, Fallos: 0",
  "data": {
    "totalCarrerasProcesadas": 19,
    "carrerasExitosas": 19,
    "carrerasFallidas": 0,
    "totalEntradasProcesadas": 76,
    "totalEntradasCreadas": 0,
    "totalEntradasActualizadas": 76,
    "resultados": [
      {
        "codigoEvento": "F1-2026-MAD",
        "idEvento": "992ae124-3d59-4adb-9fd2-f0e825c605e8",
        "carrera": "Madrid",
        "temporada": 2026,
        "fechaCarrera": "2026-09-11",
        "totalEntradasExtraidas": 4,
        "entradasCreadas": 0,
        "entradasActualizadas": 4,
        "estado": "SUCCESS",
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
          }
        ]
      }
    ]
  }
}
```

---

### 5.2. Sincronización a Demanda de un Gran Premio Específico

Permite forzar la actualización de entradas para una única carrera pasando su código legado en la URL (ej. `F1-2026-MAD`, `F1-2026-MON`, `F1-2026-SIL`, etc.).

* **Método**: `POST`
* **Ruta**: `/api/microservicios/sync-tickets/{codigoEvento}`

#### Invocación Local:
```bash
curl -X POST http://localhost:8083/api/microservicios/sync-tickets/F1-2026-MAD \
  -H "Content-Type: application/json"
```

#### Invocación en Render:
```bash
curl -X POST https://<tu-servicio>.onrender.com/api/microservicios/sync-tickets/F1-2026-MAD \
  -H "Content-Type: application/json"
```

#### Respuesta de Ejemplo (`200 OK`):
```json
{
  "success": true,
  "message": "Sincronización ETL de entradas para F1-2026-MAD (Madrid) completada con éxito. Total: 4 (Creadas: 0, Actualizadas: 4)",
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
        "precioUsd": 650.00,
        "stockDisponible": 5000,
        "tipo": "Asiento Numerado"
      },
      {
        "idEntrada": "55555555-0000-4000-8000-000000000000",
        "idEvento": "992ae124-3d59-4adb-9fd2-f0e825c605e8",
        "nombreTribuna": "Pelouse General",
        "precioUsd": 280.00,
        "stockDisponible": 12000,
        "tipo": "General"
      }
    ]
  }
}
```

---

### 5.3. Catálogo Maestro de Grandes Premios

Devuelve el listado de las 19 carreras objetivo configuradas en el sistema, con sus respectivos códigos de evento, ciudades y nombres de circuito.

* **Método**: `GET`
* **Ruta**: `/api/microservicios/sync-tickets/catalog`

#### Invocación Local:
```bash
curl -X GET http://localhost:8083/api/microservicios/sync-tickets/catalog
```

#### Invocación en Render:
```bash
curl -X GET https://<tu-servicio>.onrender.com/api/microservicios/sync-tickets/catalog
```

#### Respuesta de Ejemplo (`200 OK`):
```json
{
  "success": true,
  "message": "Catálogo de 19 Grandes Premios recuperado exitosamente",
  "data": [
    {
      "codigoEvento": "F1-2026-MAD",
      "carrera": "Madrid",
      "temporada": 2026,
      "nombreCircuito": "Circuito de Madring IFEMA",
      "ciudad": "Madrid"
    },
    {
      "codigoEvento": "F1-2026-MON",
      "carrera": "Monaco",
      "temporada": 2026,
      "nombreCircuito": "Circuit de Monaco",
      "ciudad": "Monaco"
    },
    {
      "codigoEvento": "F1-2026-SIL",
      "carrera": "Silverstone",
      "temporada": 2026,
      "nombreCircuito": "Silverstone Circuit",
      "ciudad": "Silverstone"
    }
  ]
}
```

---

### 5.4. Reserva de Entradas en el Sistema Legado SOAP

Ejecuta la reserva de entradas y la deducción de inventario **directamente en el sistema legado SOAP**, sin modificar la base de datos de Supabase (la cual es gestionada de forma autónoma por el backend principal).

#### Mecánica interna:
1. Recibe el identificador `idEntrada` (UUID de Supabase) y la `cantidad` deseada.
2. Consulta la tabla `public.entradas_gradas` en Supabase en **modo solo lectura** (`@Transactional(readOnly = true)`) para recuperar el nombre de la tribuna (`nombreTribuna`) y el identificador del evento (`idEvento`).
3. Resuelve el código de carrera SOAP correspondiente (ej: `F1-2026-MAD`).
4. Invoca la operación SOAP `reservarEntradas(codigoEvento, tribuna, cantidad)`.
5. El sistema SOAP verifica el stock remanente, descuenta las entradas y retorna un código alfanumérico de confirmación (ej: `TKT-99812`).
6. Si el stock es insuficiente, el servicio SOAP retorna un `SinStockFault`, el cual es traducido a una respuesta HTTP `409 Conflict`.

* **Método**: `POST`
* **Ruta**: `/api/microservicios/tickets/reservar` (Payload JSON)

#### Invocación Local:
```bash
curl -X POST http://localhost:8083/api/microservicios/tickets/reservar \
  -H "Content-Type: application/json" \
  -d '{
    "idEntrada": "55555555-0000-4000-8000-000000000003",
    "cantidad": 2
  }'
```

#### Invocación en Render:
```bash
curl -X POST https://<tu-servicio>.onrender.com/api/microservicios/tickets/reservar \
  -H "Content-Type: application/json" \
  -d '{
    "idEntrada": "55555555-0000-4000-8000-000000000003",
    "cantidad": 2
  }'
```

#### Respuesta Exitosa (`200 OK`):
```json
{
  "success": true,
  "message": "Reserva confirmada en el sistema SOAP con código 'TKT-99812'",
  "data": {
    "codigoConfirmacion": "TKT-99812",
    "idEntrada": "55555555-0000-4000-8000-000000000003",
    "idEvento": "992ae124-3d59-4adb-9fd2-f0e825c605e8",
    "codigoEvento": "F1-2026-MAD",
    "carrera": "Madrid",
    "nombreTribuna": "Paddock Club Madrid",
    "tipo": "VIP",
    "cantidad": 2,
    "precioUnitarioUsd": 3500.00,
    "precioTotalUsd": 7000.00,
    "estado": "SUCCESS",
    "mensaje": "Reserva confirmada exitosamente en el sistema de ticketing F1 (SOAP).",
    "fechaReserva": "2026-10-03T16:54:12.268Z"
  }
}
```

#### Respuesta de Error por Stock Insuficiente (`409 Conflict`):
```json
{
  "success": false,
  "message": "Stock insuficiente para la tribuna 'Paddock Club Madrid' en el evento 'F1-2026-MAD'. Stock disponible: 1, cantidad solicitada: 5.",
  "data": null
}
```

#### Respuesta de Error si la Entrada no Existe (`404 Not Found`):
```json
{
  "success": false,
  "message": "Entrada no encontrado con id: '55555555-0000-4000-8000-000000000003'",
  "data": null
}
```

---

## 6. Esquema de Base de Datos

El microservicio persiste sobre la tabla `public.entradas_gradas` en Supabase:

```sql
create table public.entradas_gradas (
  id_entrada uuid not null default extensions.uuid_generate_v4 (),
  id_evento uuid not null,
  nombre_tribuna character varying(100) not null,
  precio_usd numeric(10, 2) not null,
  stock_disponible integer not null default 0,
  tipo character varying(50) null,
  created_at timestamp with time zone null default timezone ('utc'::text, now()),
  constraint entradas_gradas_pkey primary key (id_entrada),
  constraint entradas_gradas_id_evento_fkey foreign KEY (id_evento) references eventos_f1 (id_evento) on delete CASCADE,
  constraint entradas_gradas_tipo_check check (
    (
      (tipo)::text = any (
        (
          array[
            'VIP'::character varying,
            'Asiento Numerado'::character varying,
            'General'::character varying
          ]
        )::text[]
      )
    )
  )
);
```

---

## 7. Ejecución Local y Testing

### 7.1. Requisitos Previos
* Java 21 LTS
* Maven 3.9+ (o utilizar el wrapper `./mvnw`)

### 7.2. Configuración de Variables (`.env`)
Crear un archivo `.env` en la raíz del proyecto (o configurar variables de entorno en el sistema) tomando como base `.env.example`:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://<SUPABASE_HOST>:6543/postgres?sslmode=require&prepareThreshold=0
SPRING_DATASOURCE_USERNAME=postgres.<PROJECT_REF>
SPRING_DATASOURCE_PASSWORD=<TU_PASSWORD>
TICKETING_WSDL_URL=http://localhost:8080/ws/ticketing.wsdl
TICKETING_SOAP_ENDPOINT_URL=http://localhost:8080/ws/ticketing
```

### 7.3. Compilar y Ejecutar Pruebas
```bash
./mvnw clean test
```

### 7.4. Iniciar la Aplicación en Modo Local
```bash
./mvnw spring-boot:run
```
La aplicación iniciará en el puerto `8083`.
