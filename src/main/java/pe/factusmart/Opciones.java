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
 *   <li>{@code limite}: solo {@code catalogos.buscarProductoSunat} (máx. 50).</li>
 *   <li>{@code nivel}: solo {@code catalogos.buscarProductoSunat}: {@code segmento}, {@code familia},
 *       {@code clase} o {@code producto} (el que va en el comprobante).</li>
 *   <li>{@code formato}: solo {@code comprobantes.descargar(id, "pdf", ...)}: {@code a4}, {@code a5},
 *       {@code ticket} (= {@code ticket80}), {@code ticket80}, {@code ticket58}, {@code ticket50}.</li>
 * </ul>
 */
public final class Opciones {

    private String ruc;
    private String idempotencyKey;
    private Number porcentajeIgv;
    private String q;
    private String formato;
    private Integer limite;
    private String nivel;

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

    /** Tamaño del PDF en {@code comprobantes.descargar(id, "pdf", ...)}. Sin formato → A4. */
    public Opciones formato(String formato) {
        this.formato = formato;
        return this;
    }

    public static Opciones conFormato(String formato) {
        return new Opciones().formato(formato);
    }

    /** Máximo de resultados en {@code catalogos.buscarProductoSunat} (el API admite hasta 50). */
    public Opciones limite(int limite) {
        this.limite = limite;
        return this;
    }

    public Integer getLimite() {
        return limite;
    }

    /**
     * Nivel UNSPSC en {@code catalogos.buscarProductoSunat}: {@code segmento}, {@code familia},
     * {@code clase} o {@code producto}. Sin nivel no se manda el parámetro.
     */
    public Opciones nivel(String nivel) {
        this.nivel = nivel;
        return this;
    }

    public String getNivel() {
        return nivel;
    }

    public String getFormato() {
        return formato;
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
