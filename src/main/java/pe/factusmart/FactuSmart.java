package pe.factusmart;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cliente del API de facturación electrónica de FactuSmart (SUNAT, Perú).
 *
 * <pre>{@code
 * FactuSmart fs = FactuSmart.builder()
 *     .apiKey("TU_API_KEY")
 *     .baseUrl("https://TU_URL_BASE/api/v1/issuer")
 *     .ruc("20123456789")
 *     .build();
 * Map<String, Object> r = fs.comprobantes.emitir(datos);
 * }</pre>
 *
 * <p>Los datos se envían y se reciben tal cual los define el API (claves en español con guion bajo),
 * como {@code Map<String, Object>} / {@code List<Object>} (ver {@link Json}).
 *
 * <p>Es inmutable y seguro para usar desde varios hilos: crea uno y reúsalo.
 */
public final class FactuSmart {

    /** Versión del SDK. */
    public static final String VERSION = "0.1.0";

    private static final long ESPERA_MAXIMA_MS = 60_000L;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Pattern FIRMA_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern ESPERA_N_SEGUNDOS = Pattern.compile("espera\\s+(\\d+)\\s*segundo", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOO_MANY = Pattern.compile("too many attempts", Pattern.CASE_INSENSITIVE);

    /** Espera entre reintentos, en milisegundos. Se puede reemplazar en pruebas. */
    @FunctionalInterface
    public interface Dormir {
        void dormir(long milisegundos) throws InterruptedException;
    }

    // ───────────── Recursos ─────────────

    /** {@code GET /account}. */
    public final Cuenta cuenta;
    /** {@code GET /series}. */
    public final Series series;
    /** {@code /documents*} y {@code /calcular}. */
    public final Comprobantes comprobantes;
    /** {@code /catalogos*}. */
    public final Catalogos catalogos;
    /** {@code /anulaciones*}. */
    public final Anulaciones anulaciones;
    /** {@code /guias*}. */
    public final Guias guias;
    /** {@code /rucs*}. */
    public final Rucs rucs;
    /** {@code /establecimientos} (locales / anexos del RUC). */
    public final Establecimientos establecimientos;

    private final String apiKey;
    private final String baseUrl;
    private final String ruc;
    private final Duration timeout;
    private final int maxReintentos;
    private final boolean verificarSsl;
    private final Transporte transporte;
    private final Dormir dormir;

    private FactuSmart(Builder b) {
        if (b.apiKey == null || b.apiKey.trim().isEmpty()) {
            throw new FactuSmartException("Falta apiKey: es la API key de tu panel de integrador (pestaña Configuración).");
        }
        if (b.baseUrl == null || b.baseUrl.trim().isEmpty()) {
            throw new FactuSmartException("Falta baseUrl: copia la \"URL base de la API\" de tu panel de integrador "
                    + "(ej. https://s2.factusmart.pe/api/v1/issuer).");
        }
        String url = b.baseUrl.trim().replaceAll("/+$", "");
        try {
            URI u = URI.create(url);
            if (u.getScheme() == null || u.getHost() == null
                    || !(u.getScheme().equalsIgnoreCase("https") || u.getScheme().equalsIgnoreCase("http"))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new FactuSmartException("baseUrl inválida: \"" + b.baseUrl + "\". Debe ser como https://s2.factusmart.pe/api/v1/issuer.");
        }
        if (b.timeout == null || b.timeout.isNegative() || b.timeout.isZero()) {
            throw new FactuSmartException("timeout debe ser mayor que cero.");
        }
        this.apiKey = b.apiKey.trim();
        this.baseUrl = url;
        this.ruc = b.ruc == null || b.ruc.trim().isEmpty() ? null : b.ruc.trim();
        this.timeout = b.timeout;
        this.maxReintentos = Math.max(0, b.maxReintentos);
        this.verificarSsl = b.verificarSsl;
        this.transporte = b.transporte != null ? b.transporte : new TransporteHttpClient(this.timeout, this.verificarSsl);
        this.dormir = b.dormir != null ? b.dormir : Thread::sleep;

        this.cuenta = new Cuenta(this);
        this.series = new Series(this);
        this.comprobantes = new Comprobantes(this);
        this.catalogos = new Catalogos(this);
        this.anulaciones = new Anulaciones(this);
        this.guias = new Guias(this);
        this.rucs = new Rucs(this);
        this.establecimientos = new Establecimientos(this);
    }

    /** Empieza a configurar un cliente. {@code apiKey} y {@code baseUrl} son obligatorias. */
    public static Builder builder() {
        return new Builder();
    }

    /** Configuración del cliente. */
    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private String ruc;
        private Duration timeout = Duration.ofSeconds(60);
        private int maxReintentos = 3;
        private boolean verificarSsl = true;
        private Transporte transporte;
        private Dormir dormir;

        private Builder() {}

        /** API key de la cuenta (header {@code X-API-Key}). Obligatoria. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** URL base de tu servidor, ej. {@code https://s2.factusmart.pe/api/v1/issuer}. Obligatoria (no hay dominio único). */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** RUC emisor por defecto. Se puede cambiar en cada llamada con {@link Opciones#ruc(String)}. */
        public Builder ruc(String ruc) {
            this.ruc = ruc;
            return this;
        }

        /** Tiempo máximo por petición, en segundos. Default 60 (SUNAT a veces tarda). */
        public Builder timeout(int segundos) {
            this.timeout = Duration.ofSeconds(segundos);
            return this;
        }

        /** Tiempo máximo por petición. Default 60 s. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Reintentos ante errores transitorios. Default 3. */
        public Builder maxReintentos(int maxReintentos) {
            this.maxReintentos = maxReintentos;
            return this;
        }

        /** Default {@code true}. En {@code false} acepta certificados propios: SOLO pruebas locales. */
        public Builder verificarSsl(boolean verificarSsl) {
            this.verificarSsl = verificarSsl;
            return this;
        }

        /** Transporte HTTP propio (proxies, pruebas sin red). Default: {@code java.net.http.HttpClient}. */
        public Builder transporte(Transporte transporte) {
            this.transporte = transporte;
            return this;
        }

        /** Función de espera entre reintentos. Solo para pruebas. */
        public Builder dormir(Dormir dormir) {
            this.dormir = dormir;
            return this;
        }

        /** @throws FactuSmartException si falta {@code apiKey} o {@code baseUrl} */
        public FactuSmart build() {
            return new FactuSmart(this);
        }
    }

    // ───────────── API pública ─────────────

    /** {@code GET /ping} — prueba la API key. No consume crédito. */
    public Map<String, Object> ping() {
        return ping(null);
    }

    /** {@code GET /ping} con otro RUC. */
    public Map<String, Object> ping(Opciones opciones) {
        return objeto(Peticion.get("/ping").query("ruc", rucDe(opciones)).reintentable(true));
    }

    /**
     * Calcula {@code items} y {@code totales} listos para {@code comprobantes.emitir}, sin red.
     *
     * <p>Cada ítem: {@code descripcion}, {@code cantidad}, {@code valor_unitario} (SIN IGV),
     * {@code afectacion} ({@code "10"} gravado 18 %, {@code "20"} exonerado, {@code "30"} inafecto,
     * {@code "40"} exportación) y opcionalmente {@code codigo_interno} y {@code unidad_de_medida}
     * (default {@code NIU}). Los montos salen como {@link java.math.BigDecimal}.
     *
     * @return mapa con {@code items} ({@code List}) y {@code totales} ({@code Map})
     * @throws IllegalArgumentException si un ítem es inválido
     */
    public static Map<String, Object> calcularComprobante(List<?> items) {
        return Calculo.calcular(items);
    }

    /**
     * Verifica la firma de un webhook de FactuSmart (header {@code X-FactuSmart-Firma}): HMAC-SHA256,
     * en hex minúscula, del cuerpo CRUDO con el secreto del webhook, sin prefijo.
     *
     * <p>Usa el cuerpo tal como llegó, nunca un JSON re-serializado. Compara en tiempo constante y
     * nunca lanza: ante datos vacíos o mal formados devuelve {@code false}.
     */
    public static boolean verificarFirmaWebhook(String cuerpoCrudo, String firma, String secreto) {
        if (cuerpoCrudo == null) return false;
        return verificarFirmaWebhook(cuerpoCrudo.getBytes(StandardCharsets.UTF_8), firma, secreto);
    }

    /** Igual que {@link #verificarFirmaWebhook(String, String, String)} con el cuerpo en bytes. */
    public static boolean verificarFirmaWebhook(byte[] cuerpoCrudo, String firma, String secreto) {
        try {
            if (cuerpoCrudo == null || firma == null || secreto == null || secreto.isEmpty()) return false;
            String recibida = firma.trim().toLowerCase(java.util.Locale.ROOT);
            if (!FIRMA_HEX.matcher(recibida).matches()) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] esperada = mac.doFinal(cuerpoCrudo);
            byte[] dada = new byte[32];
            for (int i = 0; i < 32; i++) {
                dada[i] = (byte) Integer.parseInt(recibida.substring(i * 2, i * 2 + 2), 16);
            }
            return MessageDigest.isEqual(dada, esperada); // tiempo constante
        } catch (Exception e) {
            return false;
        }
    }

    /** Fecha {@code [0]} (YYYY-MM-DD) y hora {@code [1]} (HH:MM:SS) actuales en Perú (America/Lima). */
    public static String[] fechaHoraPeru() {
        ZonedDateTime ahora = ZonedDateTime.now(LIMA);
        return new String[] {
            ahora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            ahora.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        };
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** RUC emisor por defecto, o {@code null}. */
    public String getRuc() {
        return ruc;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxReintentos() {
        return maxReintentos;
    }

    public boolean isVerificarSsl() {
        return verificarSsl;
    }

    @Override
    public String toString() {
        return "FactuSmart{baseUrl=" + baseUrl + ", ruc=" + ruc + "}"; // nunca la apiKey
    }

    // ───────────── Internos (los usan los recursos) ─────────────

    String rucDe(Opciones o) {
        return o != null && o.getRuc() != null ? o.getRuc() : ruc;
    }

    /** En POST el RUC va en el cuerpo; si el usuario ya puso {@code ruc} en sus datos, se respeta el suyo. */
    Map<String, Object> cuerpoConRuc(Map<String, ?> datos, Opciones o) {
        Map<String, Object> cuerpo = datos == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(datos);
        String r = rucDe(o);
        if (!cuerpo.containsKey("ruc") && r != null) cuerpo.put("ruc", r);
        return cuerpo;
    }

    static String segmento(String valor, String queFalta) {
        if (valor == null || valor.trim().isEmpty()) throw new FactuSmartException(queFalta);
        return codificar(valor.trim());
    }

    static String codificar(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> objeto(Peticion p) {
        return (Map<String, Object>) ejecutar(p);
    }

    byte[] bytes(Peticion p) {
        return (byte[]) ejecutar(p.binario());
    }

    private String construirUrl(Peticion p) {
        StringBuilder sb = new StringBuilder(baseUrl).append(p.ruta);
        boolean primero = true;
        for (Map.Entry<String, Object> e : p.query.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            String texto = v instanceof Number ? Json.numeroATexto((Number) v) : String.valueOf(v);
            if (texto.isEmpty()) continue;
            sb.append(primero ? '?' : '&').append(codificar(e.getKey())).append('=').append(codificar(texto));
            primero = false;
        }
        return sb.toString();
    }

    private Object ejecutar(Peticion p) {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        cabeceras.put("X-API-Key", apiKey);
        cabeceras.put("Accept", p.binario
                ? "application/pdf, application/xml, application/zip, application/json;q=0.9, */*;q=0.8"
                : "application/json");
        cabeceras.put("User-Agent", "factusmart-java/" + VERSION);
        cabeceras.putAll(p.cabeceras);
        byte[] cuerpo = null;
        if (p.cuerpo != null) {
            cabeceras.put("Content-Type", "application/json");
            cuerpo = Json.aTexto(p.cuerpo).getBytes(StandardCharsets.UTF_8);
        }
        SolicitudHttp solicitud = new SolicitudHttp(p.metodo, construirUrl(p), cabeceras, cuerpo, timeout);
        int maxIntentos = p.reintentable ? maxReintentos + 1 : 1;
        String donde = p.metodo + " " + p.ruta;

        for (int intento = 0; ; intento++) {
            boolean quedan = intento + 1 < maxIntentos;
            RespuestaHttp r;
            try {
                r = transporte.enviar(solicitud);
            } catch (HttpTimeoutException e) {
                if (quedan) {
                    esperar(null, intento);
                    continue;
                }
                throw new FactuSmartException("El API no respondió en " + timeout.getSeconds() + " s (" + donde + ").", 0, null, e);
            } catch (IOException e) {
                if (quedan) {
                    esperar(null, intento);
                    continue;
                }
                String causa = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                throw new FactuSmartException("No se pudo conectar con el API (" + donde + "): " + causa, 0, null, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FactuSmartException("Petición interrumpida (" + donde + ").", 0, null, e);
            }

            int status = r.getStatus();
            if (status >= 200 && status < 300) {
                if (p.binario) return r.getCuerpo();
                String texto = r.getCuerpoTexto();
                Object v;
                try {
                    v = Json.leer(texto);
                } catch (IllegalArgumentException e) {
                    throw new FactuSmartException("El API respondió algo que no es JSON (HTTP " + status + ").", status, texto);
                }
                if (!(v instanceof Map)) {
                    throw new FactuSmartException("El API respondió un JSON que no es un objeto (HTTP " + status + ").", status, v);
                }
                return v;
            }

            String texto = r.getCuerpoTexto();
            Object cuerpoError = texto;
            try {
                cuerpoError = Json.leer(texto);
            } catch (IllegalArgumentException e) {
                // no es JSON (p. ej. una página HTML de un proxy o de un servidor de versión anterior)
            }
            FactuSmartException error = construirError(status, cuerpoError, r.getCabecera("Retry-After"));

            // `reintentable: false` en el cuerpo manda sobre la regla por HTTP (ej. 429 LIMITE_PRUEBAS_DIARIO).
            // Sin el campo (servidor anterior) se usa la regla por HTTP. `reintentable: true` solo cuenta en
            // operaciones reintentables: en las demás maxIntentos es 1 y `quedan` siempre es false.
            boolean transitorio = !Boolean.FALSE.equals(error.getReintentable())
                    && (error instanceof LimiteDePeticiones
                        || status == 502 || status == 503 || status == 504
                        || (status == 409 && p.reintentarEn409)
                        || Boolean.TRUE.equals(error.getReintentable()));
            if (transitorio && quedan) {
                esperar(error instanceof LimiteDePeticiones ? ((LimiteDePeticiones) error).getSegundosDeEspera() : null, intento);
                continue;
            }
            throw error;
        }
    }

    private void esperar(Long segundos, int intento) {
        long ms = segundos != null ? segundos * 1000L : 1000L * (1L << Math.min(intento, 20));
        try {
            dormir.dormir(Math.min(ESPERA_MAXIMA_MS, Math.max(0, ms)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FactuSmartException("Espera entre reintentos interrumpida.", 0, null, e);
        }
    }

    /** Lee los segundos de espera del header Retry-After o de un texto "Espera N segundos". */
    static Long leerSegundosDeEspera(String retryAfter, String mensaje) {
        if (retryAfter != null && !retryAfter.trim().isEmpty()) {
            String ra = retryAfter.trim();
            try {
                double n = Double.parseDouble(ra);
                if (n >= 0 && !Double.isInfinite(n)) return (long) Math.ceil(n);
            } catch (NumberFormatException ignorado) {
                try {
                    ZonedDateTime fecha = ZonedDateTime.parse(ra, DateTimeFormatter.RFC_1123_DATE_TIME);
                    long ms = fecha.toInstant().toEpochMilli() - System.currentTimeMillis();
                    return Math.max(0L, (ms + 999) / 1000);
                } catch (Exception ignorado2) {
                    // formato desconocido: se mira el mensaje
                }
            }
        }
        if (mensaje != null) {
            Matcher m = ESPERA_N_SEGUNDOS.matcher(mensaje);
            if (m.find()) return Long.parseLong(m.group(1));
        }
        return null;
    }

    private static boolean esMapaDeValidacion(Object v) {
        if (!(v instanceof Map) || ((Map<?, ?>) v).isEmpty()) return false;
        for (Object x : ((Map<?, ?>) v).values()) if (!(x instanceof List)) return false;
        return true;
    }

    private static String mapaATexto(Map<?, ?> mapa) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : mapa.entrySet()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(e.getKey()).append(": ");
            Object msgs = e.getValue();
            if (msgs instanceof List) {
                StringBuilder partes = new StringBuilder();
                for (Object m : (List<?>) msgs) {
                    if (partes.length() > 0) partes.append(' ');
                    partes.append(m);
                }
                sb.append(partes);
            } else {
                sb.append(msgs);
            }
        }
        return sb.toString();
    }

    static FactuSmartException construirError(int status, Object cuerpo, String retryAfter) {
        String mensaje = "";
        Map<String, List<String>> errores = null;
        if (cuerpo instanceof Map) {
            Map<?, ?> c = (Map<?, ?>) cuerpo;
            Object m = c.get("message") != null ? c.get("message") : c.get("mensaje");
            if (m instanceof String) {
                mensaje = (String) m;
            } else if (esMapaDeValidacion(m)) {
                errores = FactuSmartException.leerCampos(m);
                mensaje = mapaATexto((Map<?, ?>) m);
            } else if (esMapaDeValidacion(c)) {
                errores = FactuSmartException.leerCampos(c);
                mensaje = mapaATexto(c);
            }
        }
        if (mensaje.isEmpty()) {
            String base;
            switch (status) {
                case 401: base = "API key inválida o ausente."; break;
                case 402: base = "Sin créditos disponibles en la cuenta."; break;
                case 403: base = "El RUC no está autorizado para esta cuenta."; break;
                case 404: base = "Recurso no encontrado."; break;
                case 422: base = "Datos inválidos."; break;
                case 429: base = "Límite de peticiones alcanzado."; break;
                default: base = "Error del API de FactuSmart.";
            }
            mensaje = base + " (HTTP " + status + ")";
        }

        Object codigo = cuerpo instanceof Map ? ((Map<?, ?>) cuerpo).get("codigo") : null;
        // Servidores anteriores convertían el 429 del limitador en un 500 "Too Many Attempts.".
        boolean esLimite = status == 429 || "LIMITE_PETICIONES".equals(codigo)
                || (status == 500 && TOO_MANY.matcher(mensaje).find());
        if (esLimite) {
            return new LimiteDePeticiones(mensaje, status, cuerpo, leerSegundosDeEspera(retryAfter, mensaje));
        }
        switch (status) {
            case 401: return new ErrorDeAutenticacion(mensaje, status, cuerpo);
            case 402: return new SinCreditos(mensaje, status, cuerpo);
            case 403: return new RucNoAutorizado(mensaje, status, cuerpo);
            case 404: return new NoEncontrado(mensaje, status, cuerpo);
            case 422: return new ErrorDeValidacion(mensaje, status, cuerpo, errores);
            default: return new FactuSmartException(mensaje, status, cuerpo);
        }
    }

    /** Descripción de una petición (interna). */
    static final class Peticion {
        final String metodo;
        final String ruta;
        final Map<String, Object> query = new LinkedHashMap<>();
        final Map<String, String> cabeceras = new LinkedHashMap<>();
        Map<String, Object> cuerpo;
        /** Si se puede reintentar ante red/timeout/429/502/503/504. */
        boolean reintentable;
        /** Reintentar también ante 409 (solo emitir con Idempotency-Key). */
        boolean reintentarEn409;
        boolean binario;

        private Peticion(String metodo, String ruta) {
            this.metodo = metodo;
            this.ruta = ruta;
        }

        static Peticion get(String ruta) {
            return new Peticion("GET", ruta);
        }

        static Peticion post(String ruta, Map<String, Object> cuerpo) {
            Peticion p = new Peticion("POST", ruta);
            p.cuerpo = cuerpo;
            return p;
        }

        static Peticion patch(String ruta, Map<String, Object> cuerpo) {
            Peticion p = new Peticion("PATCH", ruta);
            p.cuerpo = cuerpo;
            return p;
        }

        Peticion query(String clave, Object valor) {
            query.put(clave, valor);
            return this;
        }

        Peticion cabecera(String nombre, String valor) {
            cabeceras.put(nombre, valor);
            return this;
        }

        Peticion reintentable(boolean r) {
            this.reintentable = r;
            return this;
        }

        Peticion reintentarEn409() {
            this.reintentarEn409 = true;
            return this;
        }

        Peticion binario() {
            this.binario = true;
            return this;
        }
    }
}
