package pe.factusmart;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Petición HTTP que arma el SDK (inmutable). */
public final class SolicitudHttp {

    private final String metodo;
    private final String url;
    private final Map<String, String> cabeceras;
    private final byte[] cuerpo;
    private final Duration timeout;

    public SolicitudHttp(String metodo, String url, Map<String, String> cabeceras, byte[] cuerpo, Duration timeout) {
        this.metodo = metodo;
        this.url = url;
        this.cabeceras = Collections.unmodifiableMap(new LinkedHashMap<>(cabeceras));
        this.cuerpo = cuerpo == null ? null : cuerpo.clone();
        this.timeout = timeout;
    }

    /** {@code GET}, {@code POST} o {@code PATCH}. */
    public String getMetodo() {
        return metodo;
    }

    /** URL completa, con la query ya codificada. */
    public String getUrl() {
        return url;
    }

    public Map<String, String> getCabeceras() {
        return cabeceras;
    }

    /** Busca una cabecera sin distinguir mayúsculas. */
    public String getCabecera(String nombre) {
        for (Map.Entry<String, String> e : cabeceras.entrySet()) {
            if (e.getKey().equalsIgnoreCase(nombre)) return e.getValue();
        }
        return null;
    }

    /** Cuerpo JSON en UTF-8, o {@code null} si no lleva. */
    public byte[] getCuerpo() {
        return cuerpo == null ? null : cuerpo.clone();
    }

    /** Cuerpo como texto UTF-8, o {@code null}. */
    public String getCuerpoTexto() {
        return cuerpo == null ? null : new String(cuerpo, java.nio.charset.StandardCharsets.UTF_8);
    }

    public Duration getTimeout() {
        return timeout;
    }
}
