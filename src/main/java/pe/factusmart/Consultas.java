package pe.factusmart;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Consulta de RUC y DNI (nombre, dirección, estado, condición, ubigeo) para llenar los datos del
 * cliente antes de emitir. Se usa como {@code fs.consultas}. No emite, no gasta créditos y NO
 * necesita RUC emisor: nunca manda {@code ruc} (aunque el cliente tenga uno por defecto).
 *
 * <p>Respuesta ({@code Map}): {@code success}, {@code tipo}, {@code numero}, {@code nombre},
 * {@code direccion}, {@code estado}, {@code condicion}, {@code ubigeo} (estos cuatro pueden ser
 * {@code null}), {@code desde_cache}, {@code consultas_restantes}, {@code periodo}
 * ({@code "mes"} | {@code "dia"}) y {@code se_renueva} ({@code YYYY-MM-DD}).
 *
 * <p>Tiene su propio cupo por cuenta. Al agotarlo: {@link LimiteDePeticiones} con código
 * {@code LIMITE_CONSULTAS} y {@code reintentable: false} (no se repite; la fecha viene en
 * {@code se_renueva} de {@code e.getRespuesta()}). {@code 503 CONSULTA_NO_DISPONIBLE} sí se
 * reintenta, como cualquier GET. Número inexistente → {@link NoEncontrado}; número mal formado →
 * {@link ErrorDeValidacion}.
 */
public final class Consultas {

    /** Tipos de documento que se pueden consultar. */
    public static final List<String> TIPOS = Collections.unmodifiableList(Arrays.asList("ruc", "dni"));

    private final FactuSmart fs;

    Consultas(FactuSmart fs) {
        this.fs = fs;
    }

    /** {@code GET /consultas/ruc/{numero}} — razón social, dirección, estado, condición y ubigeo. */
    public Map<String, Object> ruc(String numero) {
        return documento("ruc", numero);
    }

    /** {@code GET /consultas/dni/{numero}} — del DNI casi siempre solo llega {@code nombre} (lo demás {@code null}). */
    public Map<String, Object> dni(String numero) {
        return documento("dni", numero);
    }

    /**
     * {@code GET /consultas/{tipo}/{numero}} — {@code tipo} = {@code "ruc"} | {@code "dni"}. Otro tipo
     * lanza {@link ErrorDeValidacion} (422, {@code DATOS_INVALIDOS}, {@code campos.tipo}) sin hacer la
     * petición. El número lo valida el servidor.
     */
    public Map<String, Object> documento(String tipo, String numero) {
        String t = tipo == null ? "" : tipo.trim().toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(t)) {
            String mensaje = "Tipo de documento inválido: \"" + tipo + "\". Usa \"ruc\" o \"dni\".";
            Map<String, Object> campos = new LinkedHashMap<>();
            campos.put("tipo", Collections.singletonList("El tipo debe ser ruc o dni."));
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("success", false);
            respuesta.put("message", mensaje);
            respuesta.put("codigo", "DATOS_INVALIDOS");
            respuesta.put("reintentable", false);
            respuesta.put("campos", campos);
            throw new ErrorDeValidacion(mensaje, 422, respuesta);
        }
        String n = FactuSmart.segmento(numero,
                "consultas.documento(tipo, numero): pasa el número (RUC de 11 dígitos o DNI de 8).");
        // Sin .query("ruc", ...): esta operación no usa RUC emisor.
        return fs.objeto(FactuSmart.Peticion.get("/consultas/" + t + "/" + n).reintentable(true));
    }
}
