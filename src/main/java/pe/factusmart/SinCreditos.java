package pe.factusmart;

/** 402: la cuenta no tiene créditos. No se emitió nada. */
public class SinCreditos extends FactuSmartException {

    private static final long serialVersionUID = 1L;

    public SinCreditos(String mensaje, int status, Object respuesta) {
        super(mensaje, status, respuesta);
    }
}
