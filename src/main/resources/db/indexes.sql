-- ====================================================================
-- Índices para la tabla public.entradas_gradas en Supabase
-- Optimiza búsquedas por evento y tribuna utilizadas en la sincronización ETL
-- ====================================================================

-- Índice sobre id_evento para agilizar consultas por carrera
CREATE INDEX IF NOT EXISTS idx_entradas_gradas_id_evento
    ON public.entradas_gradas(id_evento);

-- Índice compuesto para la operación de Upsert (id_evento, nombre_tribuna)
CREATE INDEX IF NOT EXISTS idx_entradas_gradas_evento_tribuna
    ON public.entradas_gradas(id_evento, LOWER(nombre_tribuna));

-- Índice para filtrado por categoría de tipo ('VIP', 'Asiento Numerado', 'General')
CREATE INDEX IF NOT EXISTS idx_entradas_gradas_tipo
    ON public.entradas_gradas(tipo);
