package pe.factusmart;

import java.util.Map;

/** Locales (anexos SUNAT) del RUC. Se usa como {@code fs.establecimientos}. No consumen crédito. */
public final class Establecimientos {

    private final FactuSmart fs;

    Establecimientos(FactuSmart fs) {
        this.fs = fs;
    }

    /** {@code GET /establecimientos} — locales del RUC con sus series ({@code establecimientos}). */
    public Map<String, Object> listar() {
        return listar(null);
    }

    public Map<String, Object> listar(Opciones opciones) {
        return fs.objeto(FactuSmart.Peticion.get("/establecimientos").query("ruc", fs.rucDe(opciones)).reintentable(true));
    }

    /**
     * {@code POST /establecimientos} — datos: {@code codigo_anexo}, {@code nombre}, {@code direccion},
     * {@code ubigeo} y opcionales {@code telefono}, {@code email}, {@code crear_series_por_defecto},
     * {@code series}. El RUC va en el cuerpo. <b>No se reintenta nunca</b> (no es idempotente).
     * Si el plan no admite más locales lanza {@link RucNoAutorizado} con {@code codigo}
     * {@code LIMITE_ESTABLECIMIENTOS}.
     */
    public Map<String, Object> crear(Map<String, ?> datos) {
        return crear(datos, null);
    }

    public Map<String, Object> crear(Map<String, ?> datos, Opciones opciones) {
        if (datos == null) {
            throw new FactuSmartException("establecimientos.crear(datos): pasa codigo_anexo, nombre, direccion y ubigeo.");
        }
        return fs.objeto(FactuSmart.Peticion.post("/establecimientos", fs.cuerpoConRuc(datos, opciones)).reintentable(false));
    }
}
