package pe.factusmart;

import java.util.Map;

/** Series del RUC. Se usa como {@code fs.series}. */
public final class Series {

    private final FactuSmart fs;

    Series(FactuSmart fs) {
        this.fs = fs;
    }

    /** {@code GET /series} — series del RUC con el siguiente número estimado. */
    public Map<String, Object> listar() {
        return listar(null);
    }

    public Map<String, Object> listar(Opciones opciones) {
        return fs.objeto(FactuSmart.Peticion.get("/series").query("ruc", fs.rucDe(opciones)).reintentable(true));
    }
}
