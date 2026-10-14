package pe.factusmart;

/**
 * Opciones por llamada (todas opcionales). Se encadenan:
 * {@code new Opciones().ruc("20123456789").idempotencyKey("venta-123")}, o con los atajos
 * {@code Opciones.conRuc("20123456789")} / {@code Opciones.conIdempotencyKey("venta-123")}.
 *
 * <ul>
 *   <li>{@code ruc}: sobreescribe el RUC del constructor (todas las operaciones con RUC emisor).</li>
 *   <li>{@code idempotencyKey}: solo {@code comprobantes.emitir}.</li>
 *   <li>{@code porcentajeIgv}: solo {@code comprobantes.calcular} (18 o 10.5).</li>
 *   <li>{@code q}: solo {@code catalogos.consultar} (búsqueda por código o descripción).</li>
 * </ul>
 */
public final class Opciones {

    private String ruc;
    private String idempotencyKey;
    private Number porcentajeIgv;
    private String q;

    public Opciones() {}

    public static Opciones conRuc(String ruc) {
        return new Opciones().ruc(ruc);
    }

    public static Opciones conIdempotencyKey(String clave) {
        return new Opciones().idempotencyKey(clave);
    }

    public Opciones ruc(String ruc) {
        this.ruc = ruc;
        return this;
    }

    public Opciones idempotencyKey(String clave) {
        this.idempotencyKey = clave;
        return this;
    }

    public Opciones porcentajeIgv(Number porcentaje) {
        this.porcentajeIgv = porcentaje;
        return this;
    }

    public Opciones q(String busqueda) {
        this.q = busqueda;
        return this;
    }

    public String getRuc() {
        return ruc;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Number getPorcentajeIgv() {
        return porcentajeIgv;
    }

    public String getQ() {
        return q;
    }
}
