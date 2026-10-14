package pe.factusmart;

import java.util.Map;

/** Cuenta del integrador. Se usa como {@code fs.cuenta}. */
public final class Cuenta {

    private final FactuSmart fs;

    Cuenta(FactuSmart fs) {
        this.fs = fs;
    }

    /** {@code GET /account} — saldo ({@code creditos_disponibles}), plan y RUCs. */
    public Map<String, Object> saldo() {
        return fs.objeto(FactuSmart.Peticion.get("/account").reintentable(true));
    }
}
