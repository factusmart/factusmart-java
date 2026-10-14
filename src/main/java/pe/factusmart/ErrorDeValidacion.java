package pe.factusmart;

import java.util.List;
import java.util.Map;

/** 422: datos inválidos o error del motor de emisión. El crédito no se cobra. Revisa {@link #getCampos()}. */
public class ErrorDeValidacion extends FactuSmartException {

    private static final long serialVersionUID = 1L;

    private final Map<String, List<String>> errores;

    public ErrorDeValidacion(String mensaje, int status, Object respuesta) {
        this(mensaje, status, respuesta, null);
    }

    public ErrorDeValidacion(String mensaje, int status, Object respuesta, Map<String, List<String>> errores) {
        super(mensaje, status, respuesta);
        Map<String, List<String>> campos = respuesta instanceof Map
                ? FactuSmartException.leerCampos(((Map<?, ?>) respuesta).get("campos"))
                : null;
        this.errores = errores != null ? errores : (campos == null || campos.isEmpty() ? null : campos);
    }

    /**
     * Mapa campo → mensajes en el formato de validación Laravel de versiones anteriores del API (o
     * {@code campos} si solo viene ese). {@code null} si no hay. En código nuevo usa {@link #getCampos()}.
     */
    public Map<String, List<String>> getErrores() {
        return errores;
    }
}
