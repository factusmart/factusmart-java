package pe.factusmart;

/**
 * Límite superado (HTTP 429, o 500 con {@code "Too Many Attempts."} en servidores anteriores).
 * Distingue el caso por {@link #getCodigo()}, no por la clase:
 * <ul>
 *   <li>{@code LIMITE_PETICIONES} ({@code reintentable: true}): límite por minuto; el SDK reintenta solo.</li>
 *   <li>{@code LIMITE_PRUEBAS_DIARIO} ({@code reintentable: false}): tope de 200 operaciones/día en RUCs
 *       demo. No se reintenta; el cupo se reinicia a medianoche (hora de Perú).</li>
 * </ul>
 */
public class LimiteDePeticiones extends FactuSmartException {

    private static final long serialVersionUID = 1L;

    private final Long segundosDeEspera;

    public LimiteDePeticiones(String mensaje, int status, Object respuesta, Long segundosDeEspera) {
        super(mensaje, status, respuesta);
        this.segundosDeEspera = segundosDeEspera;
    }

    /** Segundos que pide esperar el API (Retry-After o "Espera N segundos"); {@code null} si no se pudo leer. */
    public Long getSegundosDeEspera() {
        return segundosDeEspera;
    }
}
