package pe.factusmart;

import java.util.Map;

/** Catálogos SUNAT. Se usa como {@code fs.catalogos}. No consumen crédito. */
public final class Catalogos {

    private final FactuSmart fs;

    Catalogos(FactuSmart fs) {
        this.fs = fs;
    }

    /** {@code GET /catalogos} — catálogos disponibles ({@code catalogos}, {@code nota}). */
    public Map<String, Object> listar() {
        return listar(null);
    }

    public Map<String, Object> listar(Opciones opciones) {
        return fs.objeto(FactuSmart.Peticion.get("/catalogos").query("ruc", fs.rucDe(opciones)).reintentable(true));
    }

    /**
     * {@code GET /catalogos/{nombre}} — filas de un catálogo ({@code catalogo}, {@code total}, {@code datos}).
     * {@code nombre} = {@code "unidades"} o el número SUNAT ({@code "03"}). Usa solo las filas con
     * {@code activo: true}. Un nombre que no existe lanza {@link NoEncontrado}.
     */
    public Map<String, Object> consultar(String nombre) {
        return consultar(nombre, null);
    }

    /**
     * {@code GET /catalogos/productos-sunat} — busca el código de producto SUNAT (catálogo 25) por
     * código (exacto o prefijo) o por texto ({@code catalogo_sunat}, {@code q}, {@code total},
     * {@code hay_mas}, {@code datos[]} con {@code codigo} y {@code descripcion}).
     */
    public Map<String, Object> buscarProductoSunat(String q) {
        return buscarProductoSunat(q, null);
    }

    /** Igual, con {@code limite} (máx. 50) y/o {@code ruc} en {@link Opciones}. */
    public Map<String, Object> buscarProductoSunat(String q, Opciones opciones) {
        if (q == null || q.trim().isEmpty()) {
            throw new FactuSmartException("catalogos.buscarProductoSunat(q): pasa un texto (ej. \"computador\") o un código (ej. \"4321\").");
        }
        return fs.objeto(FactuSmart.Peticion.get("/catalogos/productos-sunat")
                .query("q", q.trim())
                .query("limite", opciones != null ? opciones.getLimite() : null)
                .query("ruc", fs.rucDe(opciones))
                .reintentable(true));
    }

    /** Igual, con {@code q} (búsqueda) y/o {@code ruc} en {@link Opciones}. */
    public Map<String, Object> consultar(String nombre, Opciones opciones) {
        String n = FactuSmart.segmento(nombre,
                "catalogos.consultar(nombre): pasa el nombre (ej. \"unidades\") o el número SUNAT (ej. \"03\").");
        return fs.objeto(FactuSmart.Peticion.get("/catalogos/" + n)
                .query("q", opciones != null ? opciones.getQ() : null)
                .query("ruc", fs.rucDe(opciones))
                .reintentable(true));
    }
}
