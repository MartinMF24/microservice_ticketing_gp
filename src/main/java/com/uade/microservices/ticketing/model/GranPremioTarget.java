package com.uade.microservices.ticketing.model;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Catálogo maestro de Grandes Premios de Fórmula 1 y códigos de evento legados.
 * Mapea los códigos del sistema SOAP (ej: F1-2026-MAD) con sus temporadas, nombres de carrera/ciudad,
 * fechas y los UUIDs registrados en Supabase.
 */
public enum GranPremioTarget {

    MADRID(
            "F1-2026-MAD",
            "Madrid",
            2026,
            "2026-09-11",
            UUID.fromString("992ae124-3d59-4adb-9fd2-f0e825c605e8"),
            List.of("Madrid")
    ),
    BAKU(
            "F1-2026-BAK",
            "Bakú",
            2026,
            "2026-09-25",
            UUID.fromString("f1a34d7f-3f76-446b-bed9-22fced10166e"),
            List.of("Bakú", "Baku")
    ),
    SINGAPUR(
            "F1-2026-SIN",
            "Singapur",
            2026,
            "2026-10-09",
            UUID.fromString("28780217-2728-4321-865d-db4de3d9ec26"),
            List.of("Singapur", "Singapore")
    ),
    AUSTIN(
            "F1-2026-AUS",
            "Austin",
            2026,
            "2026-10-23",
            UUID.fromString("b3ebbdfa-a64b-4e3f-a0a8-782af0b5b472"),
            List.of("Austin", "COTA", "Texas")
    ),
    CIUDAD_DE_MEXICO(
            "F1-2026-MEX",
            "Ciudad de México",
            2026,
            "2026-10-30",
            UUID.fromString("9f2762ef-6bde-4ee4-8c43-2a701e887162"),
            List.of("Ciudad de México", "Ciudad de Mexico", "Mexico", "CDMX")
    ),
    SAO_PAULO(
            "F1-2026-SAO",
            "São Paulo",
            2026,
            "2026-11-06",
            UUID.fromString("c25c1812-9ced-4f15-ad77-842e52214f05"),
            List.of("São Paulo", "Sao Paulo", "San Pablo", "Interlagos")
    ),
    LAS_VEGAS(
            "F1-2026-LAS",
            "Las Vegas",
            2026,
            "2026-11-19",
            UUID.fromString("0807f206-e3b8-4855-9ea7-2f8b8db1cfb7"),
            List.of("Las Vegas", "Vegas")
    ),
    LUSAIL(
            "F1-2026-QAT",
            "Lusail",
            2026,
            "2026-11-27",
            UUID.fromString("4513e753-0b39-4dbd-84a1-01eb594cbf09"),
            List.of("Lusail", "Qatar", "Doha")
    ),
    ABU_DABI(
            "F1-2026-ABU",
            "Abu Dabi",
            2026,
            "2026-12-04",
            UUID.fromString("691efae9-acc9-4c45-95dc-210acf720d83"),
            List.of("Abu Dabi", "Abu Dhabi", "Yas Marina")
    ),
    BAHREIN(
            "F1-2027-BAH",
            "Bahréin",
            2027,
            "2027-03-14",
            UUID.fromString("a3308380-0283-407f-b5ff-57162191f6dd"),
            List.of("Bahréin", "Bahrain", "Sakhir", "Manama")
    ),
    ARABIA_SAUDITA(
            "F1-2027-SAU",
            "Arabia Saudita",
            2027,
            "2027-03-21",
            UUID.fromString("7d9898d0-9288-4346-9f4b-f05c53ca97d7"),
            List.of("Arabia Saudita", "Saudi Arabia", "Yeda", "Jeddah")
    ),
    AUSTRALIA(
            "F1-2027-MEL",
            "Australia",
            2027,
            "2027-04-04",
            UUID.fromString("8dcec172-eff8-4d21-bd7e-3a2fa463f109"),
            List.of("Australia", "Melbourne", "Albert Park")
    ),
    JAPON(
            "F1-2027-SUZ",
            "Japón",
            2027,
            "2027-04-11",
            UUID.fromString("a63a9d28-66c4-474b-bd18-7af4153742a8"),
            List.of("Japón", "Japon", "Suzuka", "Japan")
    ),
    CHINA(
            "F1-2027-SHA",
            "China",
            2027,
            "2027-04-18",
            UUID.fromString("7f18728f-2bd2-4a39-902f-ee793116a49c"),
            List.of("China", "Shanghai", "Shanghái")
    ),
    MIAMI(
            "F1-2027-MIA",
            "Miami",
            2027,
            "2027-05-02",
            UUID.fromString("be293f66-3c56-4026-82a2-1ff2f5745123"),
            List.of("Miami", "Florida")
    ),
    CANADA(
            "F1-2027-MON",
            "Canadá",
            2027,
            "2027-05-23",
            UUID.fromString("92bca9cd-9adf-4719-8ef8-968dab71725d"),
            List.of("Canadá", "Canada", "Montreal", "Montréal")
    ),
    MONACO(
            "F1-2027-MCO",
            "Mónaco",
            2027,
            "2027-06-06",
            UUID.fromString("c70b90fa-9daf-4f4c-b0be-ee77cdf54cf4"),
            List.of("Mónaco", "Monaco", "Montecarlo", "Monte Carlo")
    ),
    PORTUGAL(
            "F1-2027-POR",
            "Portugal",
            2027,
            "2027-06-20",
            UUID.fromString("ae4d7695-9767-4d39-94e2-3d02fb5e6236"),
            List.of("Portugal", "Portimão", "Portimao", "Algarve")
    ),
    GRAN_BRETANA(
            "F1-2027-SIL",
            "Gran Bretaña",
            2027,
            "2027-07-04",
            UUID.fromString("6a75b6a1-685a-40f5-a5d2-a94f7365d1a4"),
            List.of("Gran Bretaña", "Great Britain", "Silverstone", "Reino Unido", "United Kingdom")
    );

    private final String codigoEvento;
    private final String nombreCarrera;
    private final int temporada;
    private final LocalDate fechaCarrera;
    private final UUID eventoId;
    private final List<String> aliases;

    GranPremioTarget(String codigoEvento, String nombreCarrera, int temporada, String fechaCarrera, UUID eventoId, List<String> aliases) {
        this.codigoEvento = codigoEvento;
        this.nombreCarrera = nombreCarrera;
        this.temporada = temporada;
        this.fechaCarrera = LocalDate.parse(fechaCarrera);
        this.eventoId = eventoId;
        this.aliases = aliases;
    }

    public String getCodigoEvento() {
        return codigoEvento;
    }

    public String getNombreCarrera() {
        return nombreCarrera;
    }

    public int getTemporada() {
        return temporada;
    }

    public LocalDate getFechaCarrera() {
        return fechaCarrera;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public List<String> getAliases() {
        return aliases;
    }

    /**
     * Busca el Gran Premio a partir de su código legado (ej: "F1-2026-MAD"),
     * o por nombre del enum (ej: "MADRID", "MAD").
     */
    public static Optional<GranPremioTarget> fromCodigoEvento(String rawCodigo) {
        if (rawCodigo == null || rawCodigo.isBlank()) {
            return Optional.empty();
        }
        String clean = rawCodigo.trim().toUpperCase();

        // 1. Coincidencia directa por codigoEvento (ej: F1-2026-MAD)
        for (GranPremioTarget target : values()) {
            if (target.getCodigoEvento().equalsIgnoreCase(clean)) {
                return Optional.of(target);
            }
        }

        // 2. Si el código viene sin prefijo (ej: 2026-MAD o MAD)
        for (GranPremioTarget target : values()) {
            if (target.getCodigoEvento().endsWith("-" + clean) || target.name().equalsIgnoreCase(clean)) {
                return Optional.of(target);
            }
        }

        // 3. Coincidencia por alias o nombre normalizado sin acentos
        String normalizedClean = stripAccents(clean);
        for (GranPremioTarget target : values()) {
            for (String alias : target.getAliases()) {
                if (stripAccents(alias).equalsIgnoreCase(normalizedClean)) {
                    return Optional.of(target);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Busca el Gran Premio a partir del id_evento (UUID) registrado en Supabase.
     */
    public static Optional<GranPremioTarget> fromEventoId(UUID eventoId) {
        if (eventoId == null) {
            return Optional.empty();
        }
        for (GranPremioTarget target : values()) {
            if (eventoId.equals(target.getEventoId())) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    /**
     * Resuelve el Gran Premio a partir de una entidad EventoF1, verificando su UUID
     * o por coincidencia de temporada, ciudad, circuito y alias.
     */
    public static Optional<GranPremioTarget> fromEventoF1(EventoF1 evento) {
        if (evento == null) {
            return Optional.empty();
        }
        if (evento.getIdEvento() != null) {
            Optional<GranPremioTarget> byId = fromEventoId(evento.getIdEvento());
            if (byId.isPresent()) {
                return byId;
            }
        }

        int season = evento.getTemporada() != null ? evento.getTemporada() : 2026;
        String ciudadNombre = (evento.getCircuito() != null && evento.getCircuito().getCiudad() != null
                && evento.getCircuito().getCiudad().getNombre() != null)
                ? stripAccents(evento.getCircuito().getCiudad().getNombre()).toLowerCase()
                : "";
        String circuitoNombre = (evento.getCircuito() != null && evento.getCircuito().getNombre() != null)
                ? stripAccents(evento.getCircuito().getNombre()).toLowerCase()
                : "";

        for (GranPremioTarget target : values()) {
            if (target.getTemporada() == season) {
                String targetNorm = stripAccents(target.getNombreCarrera()).toLowerCase();
                if ((!ciudadNombre.isEmpty() && (ciudadNombre.contains(targetNorm) || targetNorm.contains(ciudadNombre)))
                        || (!circuitoNombre.isEmpty() && circuitoNombre.contains(targetNorm))) {
                    return Optional.of(target);
                }
                for (String alias : target.getAliases()) {
                    String aliasNorm = stripAccents(alias).toLowerCase();
                    if ((!ciudadNombre.isEmpty() && ciudadNombre.contains(aliasNorm))
                            || (!circuitoNombre.isEmpty() && circuitoNombre.contains(aliasNorm))) {
                        return Optional.of(target);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static String stripAccents(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
