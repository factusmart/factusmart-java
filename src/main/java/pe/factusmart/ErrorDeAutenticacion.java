package pe.factusmart;

/** 401: API key ausente, inválida o cuenta desactivada. */
public class ErrorDeAutenticacion extends FactuSmartException {

    private static final long serialVersionUID = 1L;

    public ErrorDeAutenticacion(String mensaje, int status, Object respuesta) {
        super(mensaje, status, respuesta);
    }
}
