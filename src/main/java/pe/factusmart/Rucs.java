package pe.factusmart;

import java.util.LinkedHashMap;
import java.util.Map;

/** RUCs de la cuenta. Se usa como {@code fs.rucs}. Estos endpoints nunca reciben el RUC emisor. */
public final class Rucs {

    private final FactuSmart fs;

    Rucs(FactuSmart fs) {
        this.fs = fs;
    }

    /** {@code GET /rucs}. */
    public Map<String, Object> listar() {
        return fs.objeto(FactuSmart.Peticion.get("/rucs").reintentable(true));
    }

    /**
     * {@code POST /rucs} — datos: {@code ruc}, {@code empresa}, {@code correo}, {@code clave}.
     * Idempotente por RUC (si ya es tuyo devuelve {@code ya_existia: true}), por eso se reintenta.
     */
    public Map<String, Object> crear(Map<String, ?> datos) {
        if (datos == null) throw new FactuSmartException("rucs.crear(datos): pasa ruc, empresa, correo y clave.");
        return fs.objeto(FactuSmart.Peticion.post("/rucs", new LinkedHashMap<String, Object>(datos)).reintentable(true));
    }

    /** {@code PATCH /rucs/{ruc}/activation} — activa o desactiva un RUC. No se reintenta automáticamente. */
    public Map<String, Object> activar(String ruc, boolean activo) {
        String r = FactuSmart.segmento(ruc, "rucs.activar(ruc, activo): falta el RUC.");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("activo", activo);
        return fs.objeto(FactuSmart.Peticion.patch("/rucs/" + r + "/activation", cuerpo).reintentable(false));
    }
}
