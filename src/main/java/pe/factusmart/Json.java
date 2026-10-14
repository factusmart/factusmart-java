package pe.factusmart;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON mínimo, sin dependencias, para el SDK.
 *
 * <p>Representación en Java:
 * <ul>
 *   <li>objeto → {@code Map<String, Object>} ({@link LinkedHashMap}, respeta el orden)</li>
 *   <li>arreglo → {@code List<Object>} ({@link ArrayList})</li>
 *   <li>número entero → {@link Long} (o {@link BigInteger} si no cabe); número con decimales o
 *       exponente → {@link BigDecimal} (nunca {@code double}: son montos de dinero)</li>
 *   <li>texto → {@link String}; {@code true/false} → {@link Boolean}; {@code null} → {@code null}</li>
 * </ul>
 *
 * <p>Al serializar acepta además {@code Integer}, {@code Double}, arreglos Java, cualquier
 * {@link Iterable} y {@link Enum} (por su nombre).
 */
public final class Json {

    private static final int PROFUNDIDAD_MAXIMA = 512;

    private Json() {}

    // ───────────── Ayudantes para armar datos ─────────────

    /**
     * Arma un objeto JSON a partir de pares clave, valor: {@code Json.objeto("ruc", "20...", "activo", true)}.
     */
    public static Map<String, Object> objeto(Object... clavesYValores) {
        if (clavesYValores.length % 2 != 0) {
            throw new IllegalArgumentException("Json.objeto: pasa pares clave, valor (cantidad par de argumentos).");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < clavesYValores.length; i += 2) {
            Object k = clavesYValores[i];
            if (!(k instanceof String)) {
                throw new IllegalArgumentException("Json.objeto: la clave en la posición " + i + " no es texto.");
            }
            m.put((String) k, clavesYValores[i + 1]);
        }
        return m;
    }

    /** Arma una lista JSON: {@code Json.lista(item1, item2)}. */
    public static List<Object> lista(Object... valores) {
        List<Object> l = new ArrayList<>(valores.length);
        Collections.addAll(l, valores);
        return l;
    }

    // ───────────── Serializar ─────────────

    /** Convierte un valor Java a texto JSON compacto. */
    public static String aTexto(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor, -1, 0, 0);
        return sb.toString();
    }

    /** Convierte un valor Java a texto JSON con sangría de 2 espacios (igual que {@code JSON.stringify(v, null, 2)}). */
    public static String aTextoBonito(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor, 2, 0, 0);
        return sb.toString();
    }

    private static void escribir(StringBuilder sb, Object v, int sangria, int nivel, int profundidad) {
        if (profundidad > PROFUNDIDAD_MAXIMA) {
            throw new IllegalArgumentException("JSON demasiado anidado (¿referencia circular?).");
        }
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            escribirTexto(sb, (String) v);
        } else if (v instanceof Character || v instanceof Enum) {
            escribirTexto(sb, v instanceof Enum ? ((Enum<?>) v).name() : v.toString());
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v) ? "true" : "false");
        } else if (v instanceof Number) {
            sb.append(numeroATexto((Number) v));
        } else if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                saltoDeLinea(sb, sangria, nivel + 1);
                escribirTexto(sb, String.valueOf(e.getKey()));
                sb.append(sangria >= 0 ? ": " : ":");
                escribir(sb, e.getValue(), sangria, nivel + 1, profundidad + 1);
            }
            saltoDeLinea(sb, sangria, nivel);
            sb.append('}');
        } else if (v instanceof Iterable || v.getClass().isArray()) {
            List<Object> elementos = new ArrayList<>();
            if (v instanceof Iterable) {
                for (Object o : (Iterable<?>) v) elementos.add(o);
            } else {
                int n = Array.getLength(v);
                for (int i = 0; i < n; i++) elementos.add(Array.get(v, i));
            }
            if (elementos.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            for (int i = 0; i < elementos.size(); i++) {
                if (i > 0) sb.append(',');
                saltoDeLinea(sb, sangria, nivel + 1);
                escribir(sb, elementos.get(i), sangria, nivel + 1, profundidad + 1);
            }
            saltoDeLinea(sb, sangria, nivel);
            sb.append(']');
        } else {
            throw new IllegalArgumentException("No se puede convertir a JSON un " + v.getClass().getName()
                    + ". Usa Map, List, String, Number, Boolean o null.");
        }
    }

    private static void saltoDeLinea(StringBuilder sb, int sangria, int nivel) {
        if (sangria < 0) return;
        sb.append('\n');
        for (int i = 0; i < sangria * nivel; i++) sb.append(' ');
    }

    static String numeroATexto(Number n) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte || n instanceof BigInteger) {
            return n.toString();
        }
        if (n instanceof BigDecimal) {
            return ((BigDecimal) n).toPlainString();
        }
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("JSON no admite NaN ni infinito.");
        }
        // Double.toString da la representación decimal más corta del double (10.555 → "10.555").
        BigDecimal bd = new BigDecimal(Double.toString(d)).stripTrailingZeros();
        if (bd.signum() == 0) return "0";
        return bd.toPlainString();
    }

    private static void escribirTexto(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ───────────── Leer ─────────────

    /**
     * Convierte texto JSON a valores Java (ver la tabla de la clase).
     *
     * @throws IllegalArgumentException si el texto no es JSON válido
     */
    public static Object leer(String texto) {
        if (texto == null) throw new IllegalArgumentException("JSON nulo.");
        Lector l = new Lector(texto);
        l.espacios();
        Object v = l.valor(0);
        l.espacios();
        if (l.pos != texto.length()) throw l.error("contenido sobrante");
        return v;
    }

    /** Igual que {@link #leer(String)} pero exige que sea un objeto. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> leerObjeto(String texto) {
        Object v = leer(texto);
        if (!(v instanceof Map)) throw new IllegalArgumentException("El JSON no es un objeto.");
        return (Map<String, Object>) v;
    }

    private static final class Lector {
        private final String s;
        private int pos;

        Lector(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String que) {
            return new IllegalArgumentException("JSON inválido en la posición " + pos + ": " + que + ".");
        }

        void espacios() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object valor(int profundidad) {
            if (profundidad > PROFUNDIDAD_MAXIMA) throw error("demasiado anidado");
            if (pos >= s.length()) throw error("fin inesperado");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return objeto(profundidad);
                case '[': return arreglo(profundidad);
                case '"': return texto();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return numero();
                    throw error("carácter inesperado '" + c + "'");
            }
        }

        void literal(String palabra) {
            if (!s.startsWith(palabra, pos)) throw error("se esperaba " + palabra);
            pos += palabra.length();
        }

        Map<String, Object> objeto(int profundidad) {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; // {
            espacios();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                espacios();
                if (pos >= s.length() || s.charAt(pos) != '"') throw error("se esperaba una clave entre comillas");
                String clave = texto();
                espacios();
                if (pos >= s.length() || s.charAt(pos) != ':') throw error("se esperaba ':'");
                pos++;
                espacios();
                m.put(clave, valor(profundidad + 1));
                espacios();
                if (pos >= s.length()) throw error("objeto sin cerrar");
                char c = s.charAt(pos++);
                if (c == '}') return m;
                if (c != ',') {
                    pos--;
                    throw error("se esperaba ',' o '}'");
                }
            }
        }

        List<Object> arreglo(int profundidad) {
            List<Object> l = new ArrayList<>();
            pos++; // [
            espacios();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return l;
            }
            while (true) {
                espacios();
                l.add(valor(profundidad + 1));
                espacios();
                if (pos >= s.length()) throw error("arreglo sin cerrar");
                char c = s.charAt(pos++);
                if (c == ']') return l;
                if (c != ',') {
                    pos--;
                    throw error("se esperaba ',' o ']'");
                }
            }
        }

        String texto() {
            pos++; // "
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) throw error("texto sin cerrar");
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= s.length()) throw error("escape incompleto");
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw error("escape \\u incompleto");
                            int cp = 0;
                            for (int i = 0; i < 4; i++) {
                                int d = Character.digit(s.charAt(pos + i), 16);
                                if (d < 0) throw error("escape \\u inválido");
                                cp = cp * 16 + d;
                            }
                            pos += 4;
                            sb.append((char) cp); // los pares sustitutos quedan unidos en el String
                            break;
                        default:
                            pos--;
                            throw error("escape desconocido \\" + e);
                    }
                } else if (c < 0x20) {
                    pos--;
                    throw error("carácter de control sin escapar");
                } else {
                    sb.append(c);
                }
            }
        }

        Number numero() {
            int inicio = pos;
            if (s.charAt(pos) == '-') pos++;
            if (pos >= s.length()) throw error("número incompleto");
            if (s.charAt(pos) == '0') {
                pos++;
            } else if (s.charAt(pos) >= '1' && s.charAt(pos) <= '9') {
                while (pos < s.length() && Character.isDigit(s.charAt(pos)) && s.charAt(pos) < 128) pos++;
            } else {
                throw error("número inválido");
            }
            boolean decimal = false;
            if (pos < s.length() && s.charAt(pos) == '.') {
                decimal = true;
                pos++;
                int d = pos;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
                if (pos == d) throw error("faltan dígitos después del punto");
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                decimal = true;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                int d = pos;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
                if (pos == d) throw error("exponente sin dígitos");
            }
            String t = s.substring(inicio, pos);
            if (decimal) return new BigDecimal(t);
            BigInteger bi = new BigInteger(t);
            return bi.bitLength() < 64 ? (Number) bi.longValue() : bi;
        }
    }
}
