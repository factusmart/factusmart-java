package pe.factusmart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Error base del SDK (no comprobado: no obliga a {@code try/catch}). Todas las demás excepciones
 * heredan de esta.
 *
 * <p>IMPORTANTE: un HTTP 200 con {@code aceptado_por_sunat: false} NO lanza excepción. Es un
 * resultado normal que tu sistema debe revisar.
 */
public class FactuSmartException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Códigos de error que manda hoy el API (campo {@code codigo}). La lista no es cerrada: el API
     * puede agregar códigos nuevos y también manda el genérico {@code ERROR_<status>}.
     */
    public static final List<String> CODIGOS_DE_ERROR = Collections.unmodifiableList(Arrays.asList(
            "NO_AUTENTICADO",
            "PLAN_VENCIDO",
            "SIN_CREDITOS",
            "RUC_NO_AUTORIZADO",
            "RUC_DESACTIVADO",
            "LLAVE_DE_PRUEBA_EN_PRODUCCION",
            "SIN_PERMISO",
            "NO_ENCONTRADO",
            "RUTA_NO_ENCONTRADA",
            "METODO_NO_PERMITIDO",
            "EMISION_EN_CURSO",
            "CONFLICTO",
            "DATOS_INVALIDOS",
            "RUC_REQUERIDO",
            "RUC_SIN_TENANT",
            "CERTIFICADO_NO_CONFIGURADO",
            "ERROR_EMISION",
            "RECHAZADO_SUNAT",
            "REENVIO_FALLIDO",
            "SUNAT_NO_DISPONIBLE",
            "LIMITE_PETICIONES",
            "LIMITE_PRUEBAS_DIARIO",
            "ANULACION_MIXTA",
            "ERROR_INTERNO"));

    private final int status;
    private final String mensaje;
    private final transient Object respuesta;
    private final String codigo;
    private final Boolean reintentable;
    private final Map<String, List<String>> campos;

    /** Error local (validación de argumentos, red, timeout): {@code status} 0, sin respuesta. */
    public FactuSmartException(String mensaje) {
        this(mensaje, 0, null);
    }

    public FactuSmartException(String mensaje, int status, Object respuesta) {
        this(mensaje, status, respuesta, null);
    }

    public FactuSmartException(String mensaje, int status, Object respuesta, Throwable causa) {
        super(mensaje, causa);
        this.status = status;
        this.mensaje = mensaje;
        this.respuesta = respuesta;
        Map<?, ?> cuerpo = respuesta instanceof Map ? (Map<?, ?>) respuesta : Collections.emptyMap();
        Object c = cuerpo.get("codigo");
        this.codigo = c instanceof String && !((String) c).isEmpty() ? (String) c : null;
        Object r = cuerpo.get("reintentable");
        this.reintentable = r instanceof Boolean ? (Boolean) r : null;
        Map<String, List<String>> leidos = leerCampos(cuerpo.get("campos"));
        this.campos = leidos != null ? leidos : Collections.<String, List<String>>emptyMap();
    }

    /** Mapa campo → mensajes; acepta también un mensaje suelto por campo. {@code null} si no es un mapa. */
    static Map<String, List<String>> leerCampos(Object v) {
        if (!(v instanceof Map)) return null;
        Map<String, List<String>> salida = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
            Object msgs = e.getValue();
            if (msgs instanceof List) {
                List<String> l = new ArrayList<>();
                for (Object m : (List<?>) msgs) l.add(String.valueOf(m));
                salida.put(String.valueOf(e.getKey()), Collections.unmodifiableList(l));
            } else if (msgs instanceof String) {
                salida.put(String.valueOf(e.getKey()), Collections.singletonList((String) msgs));
            }
        }
        return Collections.unmodifiableMap(salida);
    }

    /** Código HTTP (0 si fue un error de red, timeout o validación local). */
    public int getStatus() {
        return status;
    }

    /** Mensaje legible del API (campo {@code message}), o descripción del error de red. */
    public String getMensaje() {
        return mensaje;
    }

    /** Cuerpo completo de la respuesta: {@code Map} si era JSON, {@code String} si no, o {@code null}. */
    public Object getRespuesta() {
        return respuesta;
    }

    /**
     * Código estable del error (campo {@code codigo} del API), ej. {@code NO_AUTENTICADO},
     * {@code DATOS_INVALIDOS}. {@code null} si el error no vino del API (red, timeout, validación
     * local) o si el servidor es de una versión anterior que no lo manda.
     */
    public String getCodigo() {
        return codigo;
    }

    /**
     * {@code true} solo si el API dijo {@code reintentable: true} (repetir la misma petición más
     * tarde puede funcionar). Para distinguir "el API no lo dijo" usa {@link #getReintentable()}.
     */
    public boolean isReintentable() {
        return Boolean.TRUE.equals(reintentable);
    }

    /**
     * Valor crudo del campo {@code reintentable}: {@code true}, {@code false}, o {@code null} si el API
     * no lo mandó (servidor de versión anterior, red, timeout, validación local).
     */
    public Boolean getReintentable() {
        return reintentable;
    }

    /** Errores por campo (campo {@code campos} del API, sobre todo en 422). Mapa vacío si no hay. */
    public final Map<String, List<String>> getCampos() {
        return campos;
    }
}
