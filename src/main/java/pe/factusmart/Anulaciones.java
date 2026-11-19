package pe.factusmart;

import java.util.Map;

/** Anulaciones de facturas (comunicación de baja) y boletas (resumen diario). Se usa como {@code fs.anulaciones}. */
public final class Anulaciones {

    private final FactuSmart fs;

    Anulaciones(FactuSmart fs) {
        this.fs = fs;
    }

    /**
     * {@code POST /anulaciones} — facturas (comunicación de baja, {@code tipo: "baja"}) o boletas
     * aceptadas (resumen diario, {@code tipo: "resumen"}); no mezcles las dos (422
     * {@code ANULACION_MIXTA}). Nunca se reintenta automáticamente.
     */
    public Map<String, Object> crear(Map<String, ?> datos) {
        return crear(datos, null);
    }

    public Map<String, Object> crear(Map<String, ?> datos, Opciones opciones) {
        if (datos == null) throw new FactuSmartException("anulaciones.crear(datos): pasa los datos de la anulación.");
        return fs.objeto(FactuSmart.Peticion.post("/anulaciones", fs.cuerpoConRuc(datos, opciones)).reintentable(false));
    }

    /** {@code GET /anulaciones/{ticket}/status} ({@code tipo}, {@code estado}, {@code documentos}). */
    public Map<String, Object> estado(String ticket) {
        return estado(ticket, null);
    }

    public Map<String, Object> estado(String ticket, Opciones opciones) {
        String t = FactuSmart.segmento(ticket, "Falta el ticket de la anulación.");
        return fs.objeto(FactuSmart.Peticion.get("/anulaciones/" + t + "/status")
                .query("ruc", fs.rucDe(opciones)).reintentable(true));
    }

    /**
     * {@code GET /anulaciones/{ticket}/{tipo}} — {@code tipo}: {@code "xml"} (el enviado) o {@code "cdr"}
     * (ZIP de SUNAT; solo cuando ya respondió, si no se lanza {@link NoEncontrado}). Sin PDF. Devuelve los bytes.
     */
    public byte[] descargar(String ticket, String tipo) {
        return descargar(ticket, tipo, null);
    }

    /** Igual, con {@code ruc} en {@link Opciones}. */
    public byte[] descargar(String ticket, String tipo, Opciones opciones) {
        if (!"xml".equals(tipo) && !"cdr".equals(tipo)) {
            throw new FactuSmartException("Tipo de archivo inválido \"" + tipo + "\": usa \"xml\" o \"cdr\" (las anulaciones no tienen PDF).");
        }
        String t = FactuSmart.segmento(ticket, "Falta el ticket de la anulación.");
        return fs.bytes(FactuSmart.Peticion.get("/anulaciones/" + t + "/" + tipo)
                .query("ruc", fs.rucDe(opciones)).reintentable(true));
    }
}
