package pe.factusmart;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Facturas, boletas y notas. Se usa como {@code fs.comprobantes}. */
public final class Comprobantes {

    private final FactuSmart fs;

    Comprobantes(FactuSmart fs) {
        this.fs = fs;
    }

    private static String id(String externalId) {
        return FactuSmart.segmento(externalId, "Falta el external_id del comprobante.");
    }

    /**
     * {@code POST /documents} — siempre con {@code Idempotency-Key} (la tuya o un UUID v4), reusada en
     * todos los reintentos. Un HTTP 200 con {@code aceptado_por_sunat: false} NO es excepción: revísalo.
     *
     * <p>Completa si no vienen: {@code numero_documento} ({@code "#"}), {@code fecha_de_emision} y
     * {@code hora_de_emision} (hora de Perú), {@code codigo_tipo_moneda} ({@code "PEN"}) y, solo en
     * 01/03, {@code codigo_tipo_operacion} ({@code "0101"}).
     */
    public Map<String, Object> emitir(Map<String, ?> datos) {
        return emitir(datos, null);
    }

    /** Igual, con {@code idempotencyKey} y/o {@code ruc} en {@link Opciones}. */
    public Map<String, Object> emitir(Map<String, ?> datos, Opciones opciones) {
        if (datos == null) throw new FactuSmartException("comprobantes.emitir(datos): pasa el comprobante como Map.");
        Map<String, Object> cuerpo = fs.cuerpoConRuc(datos, opciones);
        String[] ahora = FactuSmart.fechaHoraPeru();
        Object numero = cuerpo.get("numero_documento");
        if (numero == null || "".equals(numero)) cuerpo.put("numero_documento", "#");
        if (!cuerpo.containsKey("fecha_de_emision")) cuerpo.put("fecha_de_emision", ahora[0]);
        if (!cuerpo.containsKey("hora_de_emision")) cuerpo.put("hora_de_emision", ahora[1]);
        if (!cuerpo.containsKey("codigo_tipo_moneda")) cuerpo.put("codigo_tipo_moneda", "PEN");
        // El yaml dice que codigo_tipo_operacion "no aplica a notas": solo se completa en 01/03.
        Object tipo = cuerpo.get("codigo_tipo_documento");
        String t = tipo == null ? "" : String.valueOf(tipo);
        if (!cuerpo.containsKey("codigo_tipo_operacion") && (t.equals("01") || t.equals("03"))) {
            cuerpo.put("codigo_tipo_operacion", "0101");
        }
        String clave = opciones != null && opciones.getIdempotencyKey() != null && !opciones.getIdempotencyKey().isEmpty()
                ? opciones.getIdempotencyKey()
                : UUID.randomUUID().toString();
        return fs.objeto(FactuSmart.Peticion.post("/documents", cuerpo)
                .cabecera("Idempotency-Key", clave)
                .reintentable(true)
                .reintentarEn409());
    }

    /** {@code GET /documents} — paginación por cursor ({@code antes_de}). */
    public Map<String, Object> listar() {
        return listar(null, null);
    }

    /** Filtros como {@code limite}, {@code antes_de}, {@code codigo_tipo_documento}, fechas… (ver la doc). */
    public Map<String, Object> listar(Map<String, ?> filtros) {
        return listar(filtros, null);
    }

    public Map<String, Object> listar(Map<String, ?> filtros, Opciones opciones) {
        FactuSmart.Peticion p = FactuSmart.Peticion.get("/documents").reintentable(true);
        if (filtros != null) {
            for (Map.Entry<String, ?> e : filtros.entrySet()) p.query(e.getKey(), e.getValue());
        }
        p.query("ruc", fs.rucDe(opciones));
        return fs.objeto(p);
    }

    /** {@code GET /documents/{external_id}} — estado guardado (no consulta a SUNAT en vivo). */
    public Map<String, Object> consultar(String externalId) {
        return consultar(externalId, null);
    }

    public Map<String, Object> consultar(String externalId, Opciones opciones) {
        return fs.objeto(FactuSmart.Peticion.get("/documents/" + id(externalId))
                .query("ruc", fs.rucDe(opciones)).reintentable(true));
    }

    /**
     * {@code GET /documents/{external_id}/{pdf|xml|cdr}} — devuelve los bytes del archivo. Si no existe
     * (o el CDR aún no llegó) lanza {@link NoEncontrado}.
     */
    public byte[] descargar(String externalId, String tipo) {
        return descargar(externalId, tipo, null);
    }

    /**
     * Igual, con {@code ruc} y/o {@code formato} en {@link Opciones}. {@code formato} (solo PDF):
     * {@code a4}, {@code a5}, {@code ticket}, {@code ticket80}, {@code ticket58}, {@code ticket50};
     * sin formato → A4 (no se manda el parámetro). Con xml/cdr o un valor fuera de la lista lanza
     * {@link ErrorDeValidacion} (422, {@code DATOS_INVALIDOS}) sin hacer la petición.
     */
    public byte[] descargar(String externalId, String tipo, Opciones opciones) {
        if (!"pdf".equals(tipo) && !"xml".equals(tipo) && !"cdr".equals(tipo)) {
            throw new FactuSmartException("Tipo de archivo inválido \"" + tipo + "\": usa \"pdf\", \"xml\" o \"cdr\".");
        }
        String formato = opciones != null ? opciones.getFormato() : null;
        if (formato != null) {
            if (!"pdf".equals(tipo)) {
                throw errorDeFormato("formato solo aplica al PDF: no se puede usar con \"" + tipo + "\".");
            }
            if (!FORMATOS_PDF.contains(formato)) {
                throw errorDeFormato("formato inválido \"" + formato + "\": usa " + String.join(", ", FORMATOS_PDF) + ".");
            }
        }
        FactuSmart.Peticion p = FactuSmart.Peticion.get("/documents/" + id(externalId) + "/" + tipo).reintentable(true);
        if (formato != null) p.query("formato", formato);
        p.query("ruc", fs.rucDe(opciones));
        return fs.bytes(p);
    }

    /** Valores de {@code formato} que acepta {@code GET /documents/{id}/pdf}. */
    static final List<String> FORMATOS_PDF = Collections.unmodifiableList(
            Arrays.asList("a4", "a5", "ticket", "ticket80", "ticket58", "ticket50"));

    /** 422 local (sin petición): {@code codigo} DATOS_INVALIDOS y {@code campos.formato}. */
    private static ErrorDeValidacion errorDeFormato(String mensaje) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("formato", Collections.singletonList(mensaje));
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("success", false);
        respuesta.put("message", mensaje);
        respuesta.put("codigo", "DATOS_INVALIDOS");
        respuesta.put("reintentable", false);
        respuesta.put("campos", campos);
        respuesta.put("formatos_validos", FORMATOS_PDF);
        return new ErrorDeValidacion(mensaje, 422, respuesta);
    }

    /** {@code POST /documents/{external_id}/reenviar} — no se reintenta automáticamente. Revisa {@code aceptado_por_sunat}. */
    public Map<String, Object> reenviar(String externalId) {
        return reenviar(externalId, null);
    }

    public Map<String, Object> reenviar(String externalId, Opciones opciones) {
        return fs.objeto(FactuSmart.Peticion.post("/documents/" + id(externalId) + "/reenviar", fs.cuerpoConRuc(null, opciones))
                .reintentable(false));
    }

    /** {@code POST /documents/{external_id}/consultar-sunat} — no se reintenta automáticamente. */
    public Map<String, Object> consultarSunat(String externalId) {
        return consultarSunat(externalId, null);
    }

    public Map<String, Object> consultarSunat(String externalId, Opciones opciones) {
        return fs.objeto(FactuSmart.Peticion.post("/documents/" + id(externalId) + "/consultar-sunat", fs.cuerpoConRuc(null, opciones))
                .reintentable(false));
    }

    /** {@code GET /documents/{external_id}/historial}. */
    public Map<String, Object> historial(String externalId) {
        return historial(externalId, null);
    }

    public Map<String, Object> historial(String externalId, Opciones opciones) {
        return fs.objeto(FactuSmart.Peticion.get("/documents/" + id(externalId) + "/historial")
                .query("ruc", fs.rucDe(opciones)).reintentable(true));
    }

    /**
     * {@code POST /calcular} — FactuSmart calcula {@code items} y {@code totales} listos para
     * {@link #emitir(Map)}. No emite ni cobra crédito, no manda {@code ruc}. Admite
     * {@code precio_unitario} (con IGV) y {@code porcentajeIgv} 10.5 (Ley 31556). Se reintenta como un GET.
     * Para hacerlo sin petición (solo {@code valor_unitario} y 18 %) usa {@link FactuSmart#calcularComprobante(List)}.
     */
    public Map<String, Object> calcular(List<?> items) {
        return calcular(items, null);
    }

    public Map<String, Object> calcular(List<?> items, Opciones opciones) {
        if (items == null || items.isEmpty()) {
            throw new FactuSmartException("comprobantes.calcular(items): pasa una lista con al menos un ítem.");
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("items", items);
        if (opciones != null && opciones.getPorcentajeIgv() != null) cuerpo.put("porcentaje_igv", opciones.getPorcentajeIgv());
        return fs.objeto(FactuSmart.Peticion.post("/calcular", cuerpo).reintentable(true));
    }
}
