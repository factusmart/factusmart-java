package pe.factusmart;

/** 404: comprobante, guía, anulación, catálogo o archivo no encontrado. */
public class NoEncontrado extends FactuSmartException {

    private static final long serialVersionUID = 1L;

    public NoEncontrado(String mensaje, int status, Object respuesta) {
        super(mensaje, status, respuesta);
    }
}
