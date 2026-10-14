package pe.factusmart;

/** 403: el RUC no pertenece a la cuenta o está desactivado. */
public class RucNoAutorizado extends FactuSmartException {

    private static final long serialVersionUID = 1L;

    public RucNoAutorizado(String mensaje, int status, Object respuesta) {
        super(mensaje, status, respuesta);
    }
}
