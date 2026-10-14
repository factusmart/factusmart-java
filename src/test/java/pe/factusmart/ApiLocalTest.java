package pe.factusmart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Pruebas obligatorias contra el API LOCAL (nunca producción). Emite como máximo 3 comprobantes por
 * corrida (boleta, idempotencia y boleta con /calcular): en RUCs demo no gastan crédito, pero hay un
 * tope de 200 operaciones/día por cuenta compartido con los otros SDK (429 LIMITE_PRUEBAS_DIARIO).
 *
 * <p>Variables opcionales: FACTUSMART_BASE_URL, FACTUSMART_API_KEY, FACTUSMART_RUC. Se excluyen con
 * {@code ./gradlew test -PsoloUnitarias}.
 */
@Tag("integracion")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
class ApiLocalTest {

    static final String BASE_URL = env("FACTUSMART_BASE_URL", "https://factusmart.test/api/v1/issuer");
    static final String API_KEY = env("FACTUSMART_API_KEY", "test_apikey_demo_123");
    static final String RUC = env("FACTUSMART_RUC", "20777666555");

    static FactuSmart fs;
    static Map<String, Object> emitido;

    static String env(String nombre, String defecto) {
        String v = System.getenv(nombre);
        return v == null || v.isEmpty() ? defecto : v;
    }

    static FactuSmart nuevoCliente(String apiKey) {
        // verificarSsl(false) SOLO aquí: https://factusmart.test usa un certificado propio.
        return FactuSmart.builder().apiKey(apiKey).baseUrl(BASE_URL).ruc(RUC).verificarSsl(false).build();
    }

    @BeforeAll
    static void preparar() {
        if (BASE_URL.toLowerCase().contains("factusmart.pe")) {
            throw new IllegalStateException("Estas pruebas no se corren contra producción.");
        }
        fs = nuevoCliente(API_KEY);
    }

    static Map<String, Object> cliente() {
        return Json.objeto(
                "codigo_tipo_documento_identidad", "1",
                "numero_documento", "12345678",
                "apellidos_y_nombres_o_razon_social", "CLIENTE PRUEBA SDK JAVA");
    }

    /** Boleta pequeña a un cliente con DNI (nombre enviado para no depender de RENIEC). */
    static Map<String, Object> boleta(String descripcion) {
        Map<String, Object> calc = FactuSmart.calcularComprobante((List<Map<String, Object>>) (List<?>) Json.lista(
                Json.objeto("codigo_interno", "SDK-JAVA-01", "descripcion", descripcion, "cantidad", 2,
                        "valor_unitario", 12.5, "afectacion", "10")));
        return Json.objeto(
                "codigo_tipo_documento", "03",
                "datos_del_cliente_o_receptor", cliente(),
                "items", calc.get("items"),
                "totales", calc.get("totales"));
    }

    /** Igualdad profunda de JSON, con los números comparados por valor. */
    static boolean igualesJson(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> x = (Map<?, ?>) a;
            Map<?, ?> y = (Map<?, ?>) b;
            if (!x.keySet().equals(y.keySet())) return false;
            for (Object k : x.keySet()) if (!igualesJson(x.get(k), y.get(k))) return false;
            return true;
        }
        if (a instanceof List && b instanceof List) {
            List<?> x = (List<?>) a;
            List<?> y = (List<?>) b;
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!igualesJson(x.get(i), y.get(i))) return false;
            return true;
        }
        return a == null ? b == null : a.equals(b);
    }

    static long numero(Object v) {
        assertInstanceOf(Number.class, v);
        return ((Number) v).longValue();
    }

    @Test
    @Order(1)
    void p1_pingResponde() {
        Map<String, Object> r = fs.ping();
        assertEquals(true, r.get("success"));
        assertEquals(RUC, r.get("ruc_emisor"));
        assertInstanceOf(Number.class, r.get("creditos"));
    }

    @Test
    @Order(2)
    void p2_saldoTraeCreditosDisponibles() {
        Map<String, Object> r = fs.cuenta.saldo();
        assertEquals(true, r.get("success"));
        assertTrue(numero(r.get("creditos_disponibles")) > 0, "la cuenta local no tiene créditos");
    }

    @Test
    @Order(3)
    void p3_emitirBoletaConCalcularComprobante() {
        Map<String, Object> r = fs.comprobantes.emitir(boleta("Prueba SDK Java - boleta"));
        emitido = r;
        assertEquals(true, r.get("success"));
        assertTrue(String.valueOf(r.get("serie_numero")).matches("^B\\w{3}-\\d+$"), String.valueOf(r.get("serie_numero")));
        assertEquals("prueba", r.get("entorno"));
        assertEquals(false, r.get("tiene_validez_fiscal"));
        assertEquals(true, r.get("aceptado_por_sunat"), "SUNAT beta no aceptó: estado " + r.get("estado") + ", " + r.get("sunat"));
        System.out.println("  emitido " + r.get("serie_numero") + " (" + r.get("external_id") + ")");
    }

    @Test
    @Order(4)
    void p4_consultarHistorialYDescargarPdf() {
        assertNotNull(emitido, "la prueba 3 no emitió nada");
        String id = (String) emitido.get("external_id");
        Map<String, Object> c = fs.comprobantes.consultar(id);
        assertEquals(id, c.get("external_id"));
        assertEquals(emitido.get("serie_numero"), c.get("serie_numero"));
        assertNotNull(((Map<String, Object>) c.get("links")).get("pdf"));

        Map<String, Object> h = fs.comprobantes.historial(id);
        assertEquals(id, h.get("external_id"));
        assertFalse(((List<Object>) h.get("historial")).isEmpty());

        byte[] pdf = fs.comprobantes.descargar(id, "pdf");
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));
        System.out.println("  PDF de " + pdf.length + " bytes");
    }

    @Test
    @Order(5)
    void p5_idempotenciaMismaClaveMismoExternalIdYNoCobraDosVeces() {
        // En RUCs demo el saldo no baja: no se compara el saldo, sino credito_cobrado (si viene).
        String clave = "sdk-java-" + UUID.randomUUID();
        Map<String, Object> datos = boleta("Prueba SDK Java - idempotencia");
        Map<String, Object> r1 = fs.comprobantes.emitir(datos, Opciones.conIdempotencyKey(clave));
        Map<String, Object> r2 = fs.comprobantes.emitir(datos, Opciones.conIdempotencyKey(clave));
        assertEquals(r1.get("external_id"), r2.get("external_id"));
        assertEquals(r1.get("serie_numero"), r2.get("serie_numero"));
        if (r2.containsKey("credito_cobrado")) assertEquals(false, r2.get("credito_cobrado"), "la segunda llamada cobró: " + r2);
        System.out.println("  credito_cobrado: 1ª " + r1.get("credito_cobrado") + ", 2ª " + r2.get("credito_cobrado")
                + "; modo_demo " + r1.get("modo_demo") + ", pruebas_restantes_hoy " + r2.get("pruebas_restantes_hoy"));
    }

    @Test
    @Order(6)
    void p5b_calcularEnServidorIgualAlCasoComunYBoletaConSuResultado() throws IOException {
        String esperado = new String(Files.readAllBytes(Paths.get(System.getProperty("factusmart.raiz", "."), "resultado_caso_comun.json")),
                StandardCharsets.UTF_8);
        Map<String, Object> r = fs.comprobantes.calcular(CasoComun.entrada());
        assertEquals(true, r.get("success"));
        Map<String, Object> soloDatos = Json.objeto("items", r.get("items"), "totales", r.get("totales"));
        // Mismas claves y mismos números al centavo (5.7 == 5.70: se comparan como número, no como texto).
        assertTrue(igualesJson(Json.leer(esperado), soloDatos), "difiere del caso común:\n" + Json.aTextoBonito(soloDatos));

        // Boleta con precio CON IGV calculada por el servidor (3 × S/ 10.00 = S/ 30.00).
        Map<String, Object> c = fs.comprobantes.calcular(Json.lista(Json.objeto(
                "codigo_interno", "SDK-JAVA-02", "descripcion", "Prueba SDK Java - calcular", "cantidad", 3,
                "precio_unitario", 10, "afectacion", "10", "unidad_de_medida", "ZZ")));
        assertEquals(0, new BigDecimal(String.valueOf(((Map<String, Object>) c.get("totales")).get("total_venta"))).compareTo(new BigDecimal("30")));
        Map<String, Object> e = fs.comprobantes.emitir(Json.objeto(
                "codigo_tipo_documento", "03",
                "datos_del_cliente_o_receptor", cliente(),
                "items", c.get("items"),
                "totales", c.get("totales")));
        assertEquals(true, e.get("aceptado_por_sunat"), "SUNAT beta no aceptó: estado " + e.get("estado") + ", " + e.get("sunat"));
        assertEquals("prueba", e.get("entorno"));
        if (e.containsKey("credito_cobrado")) assertEquals(false, e.get("credito_cobrado"), "un RUC demo no debe cobrar crédito");
    }

    @Test
    @Order(7)
    void p5c_catalogos() {
        Map<String, Object> l = fs.catalogos.listar();
        assertEquals(true, l.get("success"));
        List<Map<String, Object>> catalogos = (List<Map<String, Object>>) l.get("catalogos");
        assertFalse(catalogos.isEmpty());
        assertTrue(catalogos.stream().anyMatch(x -> "afectacion-igv".equals(x.get("nombre"))));

        Map<String, Object> c = fs.catalogos.consultar("07");
        assertEquals("07", c.get("catalogo_sunat"));
        Map<String, Object> fila = ((List<Map<String, Object>>) c.get("datos")).stream()
                .filter(x -> "10".equals(x.get("codigo"))).findFirst().orElse(null);
        assertNotNull(fila, "no vino la fila 10");
        assertInstanceOf(Boolean.class, fila.get("activo"));

        Map<String, Object> u = fs.catalogos.consultar("unidades", new Opciones().q("NIU"));
        assertTrue(((List<Map<String, Object>>) u.get("datos")).stream().anyMatch(x -> "NIU".equals(x.get("codigo"))));

        NoEncontrado n = assertThrows(NoEncontrado.class, () -> fs.catalogos.consultar("no-existe-sdk"));
        assertEquals("NO_ENCONTRADO", n.getCodigo());
    }

    @Test
    @Order(8)
    void p6_apiKeyFalsaEsErrorDeAutenticacion() {
        FactuSmart falso = nuevoCliente("llave_falsa_que_no_existe");
        ErrorDeAutenticacion e = assertThrows(ErrorDeAutenticacion.class, falso::ping);
        assertEquals(401, e.getStatus());
        assertEquals("NO_AUTENTICADO", e.getCodigo());
        assertEquals(Boolean.FALSE, e.getReintentable());
    }

    @Test
    @Order(9)
    void p6b_descargaInexistenteEsNoEncontrado() {
        NoEncontrado e = assertThrows(NoEncontrado.class,
                () -> fs.comprobantes.descargar("00000000-0000-4000-8000-000000000000", "pdf"));
        assertEquals(404, e.getStatus());
        assertEquals("NO_ENCONTRADO", e.getCodigo());
        assertEquals(Boolean.FALSE, e.getReintentable());
        assertTrue(!e.getMensaje().isEmpty() && !e.getMensaje().toLowerCase().contains("<html"), e.getMensaje());
        System.out.println("  mensaje del API (descarga): " + e.getMensaje());
    }

    @Test
    @Order(10)
    void p7_payloadInvalidoEsErrorDeValidacion() {
        Map<String, Object> datos = boleta("Prueba SDK Java - inválida");
        datos.put("serie_documento", "ZZ99");
        ErrorDeValidacion e = assertThrows(ErrorDeValidacion.class, () -> fs.comprobantes.emitir(datos));
        assertEquals(422, e.getStatus());
        assertTrue(e.getMensaje().length() > 10 && !e.getMensaje().contains("{"), "mensaje poco legible: " + e.getMensaje());
        System.out.println("  mensaje del API: " + e.getMensaje());

        ErrorDeValidacion v = assertThrows(ErrorDeValidacion.class, () -> fs.comprobantes.listar(Json.objeto("limite", 500)));
        assertTrue(v.getMensaje().toLowerCase().contains("limite"), v.getMensaje());
        assertEquals("DATOS_INVALIDOS", v.getCodigo());
        assertFalse(v.getCampos().get("limite").isEmpty(), String.valueOf(v.getCampos()));
        System.out.println("  mensaje del API (listar): " + v.getMensaje());
    }

    @Test
    @Order(11)
    void extra_seriesComprobantesYRucsListan() {
        Map<String, Object> s = fs.series.listar();
        assertTrue(((List<Map<String, Object>>) s.get("series")).stream().anyMatch(x -> "03".equals(x.get("codigo_tipo_documento"))));
        Map<String, Object> l = fs.comprobantes.listar(Json.objeto("limite", 2));
        assertTrue(((List<Object>) l.get("comprobantes")).size() <= 2);
        Map<String, Object> r = fs.rucs.listar();
        assertTrue(((List<Map<String, Object>>) r.get("rucs")).stream().anyMatch(x -> RUC.equals(x.get("ruc"))));
    }
}
