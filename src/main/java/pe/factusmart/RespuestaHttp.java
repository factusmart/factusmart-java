package pe.factusmart;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Respuesta HTTP cruda que devuelve un {@link Transporte}. */
public final class RespuestaHttp {

    private final int status;
    private final Map<String, List<String>> cabeceras;
    private final byte[] cuerpo;

    public RespuestaHttp(int status, Map<String, List<String>> cabeceras, byte[] cuerpo) {
        this.status = status;
        this.cabeceras = cabeceras == null ? Collections.<String, List<String>>emptyMap() : cabeceras;
        this.cuerpo = cuerpo == null ? new byte[0] : cuerpo;
    }

    /** Atajo para pruebas: respuesta con cuerpo de texto. */
    public static RespuestaHttp de(int status, String cuerpo) {
        return new RespuestaHttp(status, null, cuerpo == null ? null : cuerpo.getBytes(StandardCharsets.UTF_8));
    }

    public int getStatus() {
        return status;
    }

    public Map<String, List<String>> getCabeceras() {
        return cabeceras;
    }

    /** Primer valor de una cabecera, sin distinguir mayúsculas; {@code null} si no viene. */
    public String getCabecera(String nombre) {
        for (Map.Entry<String, List<String>> e : cabeceras.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(nombre) && e.getValue() != null && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    public byte[] getCuerpo() {
        return cuerpo;
    }

    public String getCuerpoTexto() {
        return new String(cuerpo, StandardCharsets.UTF_8);
    }
}
