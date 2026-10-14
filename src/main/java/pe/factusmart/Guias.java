package pe.factusmart;

import java.util.Map;

/** Guías de remisión electrónicas (GRE 2.0). Se usa como {@code fs.guias}. */
public final class Guias {

    private final FactuSmart fs;

    Guias(FactuSmart fs) {
        this.fs = fs;
    }

    /**
     * {@code POST /guias}. Completa {@code numero_documento} ({@code "#"}), {@code fecha_de_emision} y
     * {@code hora_de_emision} (hora de Perú) si no vienen. No usa Idempotency-Key: nunca se reintenta
     * automáticamente. Revisa {@code enviado} en la respuesta.
     */
    public Map<String, Object> emitir(Map<String, ?> datos) {
        return emitir(datos, null);
    }

    public Map<String, Object> emitir(Map<String, ?> datos, Opciones opciones) {
        if (datos == null) throw new FactuSmartException("guias.emitir(datos): pasa la guía como Map.");
        Map<String, Object> cuerpo = fs.cuerpoConRuc(datos, opciones);
        String[] ahora = FactuSmart.fechaHoraPeru();
        if (!cuerpo.containsKey("numero_documento")) cuerpo.put("numero_documento", "#");
        if (!cuerpo.containsKey("fecha_de_emision")) cuerpo.put("fecha_de_emision", ahora[0]);
        if (!cuerpo.containsKey("hora_de_emision")) cuerpo.put("hora_de_emision", ahora[1]);
        return fs.objeto(FactuSmart.Peticion.post("/guias", cuerpo).reintentable(false));
    }

    /** {@code GET /guias/{external_id}/status}. */
    public Map<String, Object> estado(String externalId) {
        return estado(externalId, null);
    }

    public Map<String, Object> estado(String externalId, Opciones opciones) {
        String id = FactuSmart.segmento(externalId, "Falta el external_id de la guía.");
        return fs.objeto(FactuSmart.Peticion.get("/guias/" + id + "/status")
                .query("ruc", fs.rucDe(opciones)).reintentable(true));
    }
}
