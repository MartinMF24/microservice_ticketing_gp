# Microservicio ETL de Entradas F1 Ticketing (`microservice_ticketing_gp`)

Microservicio Spring Boot de ingesta y sincronización (ETL) para el catálogo de entradas de tribunas y stock de Grandes Premios de Fórmula 1. Extrae los datos desde un servicio legado SOAP (`f1-ticketing-legacy-soap`), transforma y normaliza las categorías cumpliendo las restricciones CHECK de PostgreSQL, y persiste el inventario en Supabase mediante una estrategia Upsert atómica.

---

## 1. Arquitectura y Estructura del Proyecto

El microservicio implementa la arquitectura **Package by Feature** replicando el diseño arquitectónico de `microservice_booking_Hotel_gp` y `microservice_flights_gp`:

```text
microservice_ticketing_gp/
├── pom.xml                                    # CXF codegen plugin, JAX-WS runtime y Spring Boot
├── .env.example                               # Plantilla de variables de entorno
├── .env                                       # Configuración local de variables
├── src/
│   ├── main/
│   │   ├── java/com/uade/microservices/ticketing/
│   │   │   ├── TicketingMicroserviceApplication.java
│   │   │   ├── adapter/
│   │   │   │   └── TicketingAdapter.java      # Patrón Adapter (JAX-WS -> EntradaGrada)
│   │   │   ├── config/
│   │   │   │   ├── CorsConfig.java            # Configuración CORS global
│   │   │   │   ├── TicketingSoapProperties.java # Mapeo de variables de entorno SOAP
│   │   │   │   └── TicketingSoapClientConfig.java # Bean F1TicketingSoapPort
│   │   │   ├── controller/
│   │   │   │   └── TicketingSyncController.java # Endpoints POST /all y POST /{codigoEvento}
│   │   │   ├── dto/response/
│   │   │   │   ├── EntradaSyncSummaryDto.java
│   │   │   │   ├── TicketingSyncResultDto.java
│   │   │   │   └── TicketingSyncAllSummaryDto.java
│   │   │   ├── model/
│   │   │   │   ├── EntradaGrada.java          # Entidad JPA public.entradas_gradas
│   │   │   │   ├── EventoF1.java              # Entidad JPA public.eventos_f1
│   │   │   │   ├── Circuito.java              # Entidad JPA public.circuitos
│   │   │   │   ├── Ciudad.java                # Entidad JPA public.ciudades
│   │   │   │   └── GranPremioTarget.java      # Catálogo maestro de 19 Grandes Premios
│   │   │   ├── repository/
│   │   │   │   ├── EntradaGradaRepository.java
│   │   │   │   ├── EventoF1Repository.java
│   │   │   │   ├── CircuitoRepository.java
│   │   │   │   └── CiudadRepository.java
│   │   │   ├── service/
│   │   │   │   ├── TicketingClientService.java # Fase EXTRACT (JAX-WS)
│   │   │   │   └── TicketingSyncService.java   # Orquestador ETL y Upsert
│   │   │   └── shared/
│   │   │       ├── exception/
│   │   │       │   └── GlobalExceptionHandler.java
│   │   │       └── response/
│   │   │           └── ApiResponse.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-dev.properties
│   │       ├── db/
│   │       │   └── indexes.sql
│   │       └── wsdl/
│   │           └── ticketing.wsdl
│   └── test/
│       └── java/com/uade/microservices/ticketing/
│           ├── adapter/
│           │   └── TicketingAdapterTest.java
│           ├── controller/
│           │   └── TicketingSyncControllerTest.java
│           └── service/
│               └── TicketingSyncServiceTest.java
```

---

## 2. Generación del Cliente SOAP (JAX-WS)

El proyecto utiliza `cxf-codegen-plugin` (`wsdl2java`) para compilar el contrato WSDL expuesto por `f1-ticketing-legacy-soap` y generar automáticamente los stubs Java en `target/generated-sources/cxf`:

- **Variable de Entorno**: `TICKETING_WSDL_URL` (por defecto: `http://localhost:8080/ws/ticketing.wsdl`).
- **Resiliencia de Compilación Offline**: Si la variable de entorno no está establecida o el servicio SOAP no está ejecutándose durante la fase de compilación Maven, el build utiliza como fallback el contrato empaquetado en `src/main/resources/wsdl/ticketing.wsdl`.

Para generar las fuentes manualmente:
```bash
./mvnw generate-sources
```

---

## 3. Esquema de Base de Datos y Entidad JPA

La entidad `EntradaGrada` mapea estrictamente la tabla `public.entradas_gradas` de PostgreSQL / Supabase:

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

## 4. Pipeline ETL y Reglas de Negocio

### 4.1. Extract (`TicketingClientService`)
- Invoca la operación SOAP `consultarDisponibilidad(ConsultarDisponibilidadRequest)` pasando el código de carrera legado (ej. `"F1-2026-MAD"`).
- Devuelve `List<InventarioGrada>`.
- Posee fallback de contingencia configurable vía `TICKETING_SOAP_FALLBACK_ENABLED`.

### 4.2. Transform (`TicketingAdapter`)
Mapea la respuesta generada por JAX-WS a la entidad `EntradaGrada`.

> **Regla de Negocio Crítica (Restricción CHECK `entradas_gradas_tipo_check`)**:
> El sistema SOAP legado devuelve categorías como `"VIP"`, `"Premium"`, `"Standard"` y `"General"`.
> La base de datos solo admite `'VIP'`, `'Asiento Numerado'` y `'General'`.
> El Adapter transforma `"Premium"` y `"Standard"` hacia `"Asiento Numerado"`.

### 4.3. Resolución de Claves Foráneas (`EventoF1Repository`)
- Resuelve dinámicamente el `id_evento` (UUID) en Supabase a partir de la temporada (año) y el nombre de circuito o ciudad derivados del `codigoEvento` legado.
- Soporta normalización fonética y sin acentos (`"Bakú"` == `"Baku"`, `"São Paulo"` == `"Sao Paulo"`).

### 4.4. Load (`TicketingSyncService` y Repositorios - Upsert)
- Busca si ya existe una entrada con el mismo `id_evento` y `nombre_tribuna` (insensible a mayúsculas y minúsculas).
- **Si ya existe**: Actualiza `precio_usd`, `stock_disponible` y `tipo`.
- **Si no existe**: Inserta el nuevo registro con UUID generado.

---

## 5. Endpoints REST

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/microservicios/sync-tickets/all` | Sincronización masiva de todos los Grandes Premios |
| `POST` | `/api/microservicios/sync-tickets/{codigoEvento}` | Sincronización a demanda de una carrera (ej. `F1-2026-MAD`) |
| `GET` | `/api/microservicios/sync-tickets/catalog` | Catálogo maestro de Grandes Premios disponibles |

### Ejemplo de Petición Individual:
```bash
curl -X POST http://localhost:8083/api/microservicios/sync-tickets/F1-2026-MAD
```

**Respuesta Exitosa (200 OK):**
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
      }
    ]
  }
}
```

---

## 6. Ejecución y Pruebas

### Compilación y Pruebas Unitarias:
```bash
./mvnw clean test
```

### Ejecución Local:
```bash
./mvnw spring-boot:run
```
El servicio iniciará en el puerto `8083`.

---

## 7. Despliegue en la Nube (Render.com) y Configuración del CRON Diario

### 7.1. Particularidad de Render Free Tier y Estrategia de CRON
En el plan gratuito de Render, los Web Services entran en **estado de reposo (sleep)** tras 15 minutos sin peticiones entrantes.
Para asegurar la ejecución diaria de la sincronización de stock de entradas existen dos enfoques complementarios:

1. **CRON Interno en Spring Boot (`@Scheduled`)**:
   - Activo por defecto con la propiedad: `ticketing.sync.cron.enabled=true`.
   - Expresión por defecto: `0 0 3 * * ?` (3:00 AM UTC todos los días).
   - Funciona automáticamente cuando el servicio está activo o en planes con disponibilidad continua.

2. **Trigger Externo / Render Cron Job (Recomendado para despertar la app en Render Free)**:
   - Realiza una llamada HTTP `POST` a `/api/microservicios/sync-tickets/all`.
   - Al recibir la petición, Render despierta el contenedor y ejecuta el proceso ETL completo de los 19 Grandes Premios.

---

### 7.2. Paso a Paso para Desplegar en Render

#### Paso 1: Subir el proyecto a un repositorio en GitHub
Si aún no has inicializado el repositorio en GitHub:
```powershell
git init
git add .
git commit -m "feat: microservicio ETL ticketing con Dockerfile y CRON diario"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/microservice_ticketing_gp.git
git push -u origin main
```

#### Paso 2: Crear el Web Service en Render
1. Inicia sesión en [render.com](https://render.com).
2. Haz clic en **New +** y selecciona **Web Service**.
3. Conecta tu repositorio `microservice_ticketing_gp`.
4. Configura los parámetros:
   - **Name**: `microservice-ticketing-gp`
   - **Region**: Selecciona la más cercana (ej. `Oregon (US West)` o `Ohio (US East)` para proximidad a Supabase).
   - **Branch**: `main`
   - **Runtime**: `Docker`
   - **Instance Type**: `Free`

#### Paso 3: Configurar Variables de Entorno en Render
En la pestaña **Environment** de tu Web Service en Render, añade las siguientes variables:

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-0-us-west-2.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0` |
| `SPRING_DATASOURCE_USERNAME` | `postgres.zprznayvpeijjoiknird` |
| `SPRING_DATASOURCE_PASSWORD` | `uade123uade` |
| `TICKETING_WSDL_URL` | `https://tu-soap-app.onrender.com/ws/ticketing.wsdl` (o dejar default si pruebas con el contrato empaquetado) |
| `TICKETING_SOAP_ENDPOINT_URL` | `https://tu-soap-app.onrender.com/ws/ticketing` |
| `TICKETING_CRON_ENABLED` | `true` |
| `TICKETING_CRON_EXPRESSION` | `0 0 3 * * ?` |
| `HIKARI_MAX_POOL_SIZE` | `3` |

Haz clic en **Deploy Web Service**.

---

### 7.3. Configurar el Disparador Diario 100% Gratuito (Despierta Render)

En Render, la característica de "Cron Job" nativa es de pago ($1/mes). Para mantener todo **100% gratuito**, se proveen dos alternativas estándar:

#### Opción 1 (Recomendada): GitHub Actions (Ya configurado en el proyecto)
El repositorio ya incluye el workflow en [`.github/workflows/daily-sync.yml`](file:///.github/workflows/daily-sync.yml):
1. No requiere cuentas externas; corre directamente en tu repositorio de GitHub.
2. Está programado para ejecutarse todos los días a las `06:00 UTC` (03:00 AM hora Argentina):
   - Envía un `POST` al endpoint `/api/microservicios/sync-tickets/all`.
   - Incorpora reintentos automáticos (`--retry 3`) para esperar mientras Render despierta del modo reposo (cold start).
3. **Configurar la URL en GitHub**:
   - En tu repositorio de GitHub, ve a **Settings** > **Secrets and variables** > **Actions** > pestaña **Variables**.
   - Añade una variable:
     - Name: `TICKETING_SERVICE_URL`
     - Value: `https://tu-app.onrender.com` (sin la barra final).
4. **Ejecución manual a demanda**:
   - Puedes ir a la pestaña **Actions** en GitHub, seleccionar **"Sincronización Diaria de Entradas F1 (CRON)"** y presionar **Run workflow**.

#### Opción 2: cron-job.org (Gratuito sin código)
1. Regístrate en [cron-job.org](https://cron-job.org) (servicio 100% gratuito sin tarjeta de crédito).
2. Haz clic en **Create Cronjob**:
   - **Title**: `Sync F1 Tickets Render`
   - **URL**: `https://tu-app.onrender.com/api/microservicios/sync-tickets/all`
   - **Request Method**: `POST`
   - **Schedule**: User-defined (ej: todos los días a las 03:00 AM).
3. Presiona **Create**. Hará el ping diario despertando el servicio en Render y ejecutando el ETL.

