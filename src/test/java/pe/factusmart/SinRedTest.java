package pe.factusmart;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pruebas sin red: transporte falso inyectado y espera entre reintentos sin dormir de verdad. */
class SinRedTest {

    static final String BASE = "https://ejemplo.test/api/v1/issuer";

    /** Transporte falso: devuelve respuestas (o lanza IOExceptions) en orden y guarda las peticiones. */
    static final class Falso implements Transporte {
        final Deque<Object> guion = new ArrayDeque<>();
        final List<SolicitudHttp> peticiones = new ArrayList<>();

        Falso responde(int status, String cuerpo) {
            guion.add(RespuestaHttp.de(status, cuerpo));
            return this;
        }

        Falso responde(RespuestaHttp r) {
            guion.add(r);
            return this;
        }

        Falso falla(IOException e) {
            guion.add(e);
            return this;
        }

        @Override
        public RespuestaHttp enviar(SolicitudHttp s) throws IOException {
            peticiones.add(s);
            Object siguiente = guion.poll();
            if (siguiente == null) throw new AssertionError("petición inesperada: " + s.getMetodo() + " " + s.getUrl());
            if (siguiente instanceof IOException) throw (IOException) siguiente;
            return (RespuestaHttp) siguiente;
        }
    }

    final List<Long> esperas = new ArrayList<>();

    FactuSmart cliente(Falso t) {
        return FactuSmart.builder().apiKey("clave").baseUrl(BASE + "/").ruc("20100070970")
                .transporte(t).dormir(esperas::add).build();
    }

    static Map<String, Object> boleta() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("codigo_tipo_documento", "03");
        d.put("items", Json.lista());
        return d;
    }

    // ───────────── 9. calcularComprobante (caso común) ─────────────

    @Test
    void casoComunIgualAlArchivoYAlDeNode() throws IOException {
        String obtenido = CasoComun.resultadoComoTexto();
        Path raiz = Paths.get(System.getProperty("factusmart.raiz", "."));
        String propio = new String(Files.readAllBytes(raiz.resolve("resultado_caso_comun.json")), StandardCharsets.UTF_8);
        assertEquals(propio.replace("\r\n", "\n"), obtenido, "resultado_caso_comun.json está desactualizado: corre ./gradlew casoComun");

        Path node = raiz.resolve("../factusmart-node/resultado_caso_comun.json").normalize();
        if (Files.exists(node)) {
            String deNode = new String(Files.readAllBytes(node), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(deNode, obtenido, "Java y Node no coinciden al centavo");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void calcularComprobanteRedondeaMitadHaciaArribaYValida() {
        Map<String, Object> r = FactuSmart.calcularComprobante(CasoComun.entrada());
        Map<String, Object> a = ((List<Map<String, Object>>) r.get("items")).get(0);
        assertEquals(new BigDecimal("31.67"), a.get("total_base_igv")); // 10.555 × 3 = 31.665 → 31.67
        assertEquals("100", ((BigDecimal) ((Map<String, Object>) r.get("totales")).get("total_exportacion")).toString());
        assertFalse(a.containsKey("codigo_interno"));

        List<Map<String, Object>> conCodigo = Collections.singletonList(
                Json.objeto("codigo_interno", "P1", "descripcion", "X", "cantidad", "2", "valor_unitario", new BigDecimal("1.005"),
                        "afectacion", 10, "unidad_de_medida", "ZZ"));
        Map<String, Object> i = ((List<Map<String, Object>>) FactuSmart.calcularComprobante(conCodigo).get("items")).get(0);
        assertEquals("P1", i.get("codigo_interno"));
        assertEquals("ZZ", i.get("unidad_de_medida"));
        assertEquals(new BigDecimal("2.01"), i.get("total_base_igv"));

        assertThrows(IllegalArgumentException.class, () -> FactuSmart.calcularComprobante(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> FactuSmart.calcularComprobante(Collections.singletonList(
                Json.objeto("descripcion", "X", "cantidad", 1, "valor_unitario", 1, "afectacion", "11"))));
        assertThrows(IllegalArgumentException.class, () -> FactuSmart.calcularComprobante(Collections.singletonList(
                Json.objeto("descripcion", "X", "cantidad", 0, "valor_unitario", 1, "afectacion", "10"))));
        assertThrows(IllegalArgumentException.class, () -> FactuSmart.calcularComprobante(Collections.singletonList(
                Json.objeto("descripcion", "X", "cantidad", 1, "valor_unitario", -1, "afectacion", "10"))));
    }

    // ───────────── 6c. Webhooks ─────────────

    @Test
    void firmaWebhookVectorComunYCasosInvalidos() {
        String cuerpo = "{\"evento\":\"comprobante.aceptado\"}";
        String firma = "bc1542f6420851152ae42abd2cbcfe8f651cba4a2822ddd07a15d0fe036f72da";
        assertTrue(FactuSmart.verificarFirmaWebhook(cuerpo, firma, "whsec_prueba"));
        assertTrue(FactuSmart.verificarFirmaWebhook(cuerpo.getBytes(StandardCharsets.UTF_8), firma, "whsec_prueba"));
        assertTrue(FactuSmart.verificarFirmaWebhook(cuerpo, " " + firma.toUpperCase() + " ", "whsec_prueba"));

        assertFalse(FactuSmart.verificarFirmaWebhook(cuerpo, firma, "otro_secreto"));
        assertFalse(FactuSmart.verificarFirmaWebhook("{\"evento\": \"comprobante.aceptado\"}", firma, "whsec_prueba"));
        assertFalse(FactuSmart.verificarFirmaWebhook(cuerpo, "", "whsec_prueba"));
        assertFalse(FactuSmart.verificarFirmaWebhook(cuerpo, null, "whsec_prueba"));
        assertFalse(FactuSmart.verificarFirmaWebhook(cuerpo, firma, ""));
        assertFalse(FactuSmart.verificarFirmaWebhook(cuerpo, firma, null));
        assertFalse(FactuSmart.verificarFirmaWebhook((String) null, firma, "whsec_prueba"));
        assertFalse(FactuSmart.verificarFirmaWebhook(cuerpo, "sha256=" + firma, "whsec_prueba"));
        assertFalse(FactuSmart.verificarFirmaWebhook(cuerpo, firma.substring(1), "whsec_prueba"));
        assertFalse(FactuSmart.verificarFirmaWebhook(cuerpo, firma.replace('b', 'z'), "whsec_prueba"));
    }

    // ───────────── JSON ─────────────

    @Test
    void jsonIdaYVuelta() {
        String t = "{\"a\":1,\"b\":-2.50,\"c\":[true,false,null,\"x\\\"y\\\\z\\n\\u00e1\\ud83d\\ude00\"],\"d\":{},\"e\":[],"
                + "\"f\":1e3,\"g\":12345678901234567890,\"h\":\"\\/\"}";
        Map<String, Object> m = Json.leerObjeto(t);
        assertEquals(1L, m.get("a"));
        assertEquals(new BigDecimal("-2.50"), m.get("b"));
        assertEquals(Arrays.asList(true, false, null, "x\"y\\z\ná\uD83D\uDE00"), m.get("c"));
        assertEquals(Collections.emptyMap(), m.get("d"));
        assertEquals(new BigDecimal("1e3"), m.get("f"));
        assertEquals(new BigInteger("12345678901234567890"), m.get("g"));
        assertEquals("/", m.get("h"));
        assertEquals("1000", Json.aTexto(m.remove("f")));
        assertEquals(m, Json.leerObjeto(Json.aTexto(m)));
        assertEquals("{\"x\":[1,2.5,\"\\u0001\"],\"y\":10.555,\"z\":100,\"w\":[1,2]}",
                Json.aTexto(Json.objeto("x", Json.lista(1, 2.5, "\u0001"), "y", 10.555, "z", 100.0, "w", new int[] {1, 2})));
        assertEquals("{\n  \"a\": [\n    1\n  ],\n  \"b\": {}\n}", Json.aTextoBonito(Json.objeto("a", Json.lista(1), "b", Json.objeto())));

        for (String malo : new String[] {"", "{", "[1,]", "{\"a\" 1}", "01", "1.", "-", "tru", "\"a", "{\"a\":1}x", "\"\\x\"", "[1 2]"}) {
            assertThrows(IllegalArgumentException.class, () -> Json.leer(malo), malo);
        }
        assertThrows(IllegalArgumentException.class, () -> Json.aTexto(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.aTexto(new Object()));
    }

    // ───────────── Constructor ─────────────

    @Test
    void constructorExigeApiKeyYBaseUrl() {
        FactuSmartException e = assertThrows(FactuSmartException.class, () -> FactuSmart.builder().baseUrl(BASE).build());
        assertTrue(e.getMensaje().contains("apiKey"));
        e = assertThrows(FactuSmartException.class, () -> FactuSmart.builder().apiKey("x").build());
        assertTrue(e.getMensaje().contains("baseUrl"));
        assertThrows(FactuSmartException.class, () -> FactuSmart.builder().apiKey("x").baseUrl("no es url").build());
        FactuSmart fs = FactuSmart.builder().apiKey("x").baseUrl(BASE + "///").build();
        assertEquals(BASE, fs.getBaseUrl());
        assertEquals(60, fs.getTimeout().getSeconds());
        assertEquals(3, fs.getMaxReintentos());
        assertTrue(fs.isVerificarSsl());
        assertFalse(fs.toString().contains("x}"));
    }

    // ───────────── Peticiones ─────────────

    @Test
    void getLlevaRucEnQueryYCabeceras() {
        Falso t = new Falso().responde(200, "{\"success\":true}").responde(200, "{\"success\":true}");
        FactuSmart fs = cliente(t);
        fs.ping();
        fs.catalogos.consultar("unidades", new Opciones().q("kilo gramo").ruc("20999999999"));
        SolicitudHttp p = t.peticiones.get(0);
        assertEquals("GET", p.getMetodo());
        assertEquals(BASE + "/ping?ruc=20100070970", p.getUrl());
        assertEquals("clave", p.getCabecera("x-api-key"));
        assertEquals("application/json", p.getCabecera("Accept"));
        assertEquals("factusmart-java/" + FactuSmart.VERSION, p.getCabecera("User-Agent"));
        assertNull(p.getCuerpo());
        assertEquals(BASE + "/catalogos/unidades?q=kilo%20gramo&ruc=20999999999", t.peticiones.get(1).getUrl());
    }

    @Test
    void emitirCompletaDefaultsRespetaLosDelUsuarioYMandaIdempotencyKey() {
        Falso t = new Falso().responde(200, "{\"success\":true,\"aceptado_por_sunat\":true}")
                .responde(200, "{\"success\":true}");
        FactuSmart fs = cliente(t);
        fs.comprobantes.emitir(boleta());
        Map<String, Object> c = Json.leerObjeto(t.peticiones.get(0).getCuerpoTexto());
        assertEquals("20100070970", c.get("ruc"));
        assertEquals("#", c.get("numero_documento"));
        assertEquals("PEN", c.get("codigo_tipo_moneda"));
        assertEquals("0101", c.get("codigo_tipo_operacion"));
        assertTrue(((String) c.get("fecha_de_emision")).matches("\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(((String) c.get("hora_de_emision")).matches("\\d{2}:\\d{2}:\\d{2}"));
        assertEquals("application/json", t.peticiones.get(0).getCabecera("Content-Type"));
        assertTrue(t.peticiones.get(0).getCabecera("Idempotency-Key")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));

        Map<String, Object> nota = Json.objeto("codigo_tipo_documento", "07", "ruc", "20131312955", "numero_documento", 15,
                "fecha_de_emision", "2026-10-01", "codigo_tipo_moneda", "USD");
        fs.comprobantes.emitir(nota, Opciones.conIdempotencyKey("venta-1").ruc("20888888888"));
        Map<String, Object> c2 = Json.leerObjeto(t.peticiones.get(1).getCuerpoTexto());
        assertEquals("20131312955", c2.get("ruc"));
        assertEquals(15L, c2.get("numero_documento"));
        assertEquals("2026-10-01", c2.get("fecha_de_emision"));
        assertEquals("USD", c2.get("codigo_tipo_moneda"));
        assertFalse(c2.containsKey("codigo_tipo_operacion"), "en notas no aplica");
        assertEquals("venta-1", t.peticiones.get(1).getCabecera("Idempotency-Key"));
        assertFalse(nota.containsKey("hora_de_emision"), "no se modifica el Map del usuario");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ejemploDelReadmeCompilaYFunciona() {
        Falso t = new Falso().responde(200, "{\"success\":true,\"aceptado_por_sunat\":true,\"serie_numero\":\"F001-1\"}");
        FactuSmart fs = FactuSmart.builder().apiKey("TU_API_KEY").baseUrl("https://tu-servidor.test/api/v1/issuer").ruc("20100070970")
                .transporte(t).build();

        Map<String, Object> calc = FactuSmart.calcularComprobante(Json.lista(
                Json.objeto("descripcion", "Servicio de consultoría", "cantidad", 1, "valor_unitario", 100, "afectacion", "10")));

        Map<String, Object> r = fs.comprobantes.emitir(Json.objeto(
                "codigo_tipo_documento", "01",
                "datos_del_cliente_o_receptor", Json.objeto("codigo_tipo_documento_identidad", "6", "numero_documento", "20131312955"),
                "items", calc.get("items"), "totales", calc.get("totales")));

        assertEquals("true F001-1", r.get("aceptado_por_sunat") + " " + r.get("serie_numero"));
        Map<String, Object> enviado = Json.leerObjeto(t.peticiones.get(0).getCuerpoTexto());
        assertEquals(118L, ((Map<String, Object>) enviado.get("totales")).get("total_venta"));
        assertThrows(IllegalArgumentException.class, () -> FactuSmart.calcularComprobante(Json.lista("no es Map")));
    }

    @Test
    void aceptadoPorSunatFalseNoEsExcepcion() {
        Falso t = new Falso().responde(200, "{\"success\":true,\"aceptado_por_sunat\":false,\"estado\":\"01\"}");
        Map<String, Object> r = cliente(t).comprobantes.emitir(boleta());
        assertEquals(Boolean.FALSE, r.get("aceptado_por_sunat"));
    }

    @Test
    void emitirReusaLaMismaClaveEnReintentos503Y409() {
        Falso t = new Falso()
                .responde(503, "<html>caído</html>")
                .falla(new IOException("conexión reiniciada"))
                .responde(409, "{\"success\":false,\"message\":\"Ya hay una emisión en curso\",\"codigo\":\"EMISION_EN_CURSO\",\"reintentable\":true}")
                .responde(201, "{\"success\":true,\"external_id\":\"abc\"}");
        Map<String, Object> r = cliente(t).comprobantes.emitir(boleta());
        assertEquals("abc", r.get("external_id"));
        assertEquals(4, t.peticiones.size());
        String clave = t.peticiones.get(0).getCabecera("Idempotency-Key");
        for (SolicitudHttp p : t.peticiones) assertEquals(clave, p.getCabecera("Idempotency-Key"));
        assertEquals(Arrays.asList(1000L, 2000L, 4000L), esperas);
    }

    @Test
    void limitePruebasDiarioNoSeReintenta() {
        Falso t = new Falso().responde(429, "{\"success\":false,\"message\":\"Llegaste al tope de 200 pruebas de hoy.\","
                + "\"codigo\":\"LIMITE_PRUEBAS_DIARIO\",\"reintentable\":false}");
        LimiteDePeticiones e = assertThrows(LimiteDePeticiones.class, () -> cliente(t).comprobantes.emitir(boleta()));
        assertEquals(1, t.peticiones.size());
        assertEquals("LIMITE_PRUEBAS_DIARIO", e.getCodigo());
        assertEquals(Boolean.FALSE, e.getReintentable());
        assertFalse(e.isReintentable());
        assertEquals(429, e.getStatus());
        assertTrue(esperas.isEmpty());
    }

    @Test
    void reintentableFalseMandaSobreElHttp() {
        Falso t = new Falso().responde(503, "{\"success\":false,\"message\":\"x\",\"codigo\":\"CONFLICTO\",\"reintentable\":false}");
        FactuSmartException e = assertThrows(FactuSmartException.class, () -> cliente(t).cuenta.saldo());
        assertEquals(1, t.peticiones.size());
        assertEquals(503, e.getStatus());
    }

    @Test
    void limitePorMinutoRespetaRetryAfterYElMensaje() {
        Map<String, List<String>> cab = new LinkedHashMap<>();
        cab.put("retry-after", Collections.singletonList("7"));
        Falso t = new Falso()
                .responde(new RespuestaHttp(429, cab, "{\"message\":\"Demasiadas\",\"codigo\":\"LIMITE_PETICIONES\",\"reintentable\":true}".getBytes(StandardCharsets.UTF_8)))
                .responde(500, "{\"message\":\"Too Many Attempts. Espera 3 segundos.\"}")
                .responde(200, "{\"success\":true}");
        cliente(t).series.listar();
        assertEquals(Arrays.asList(7000L, 3000L), esperas);
    }

    @Test
    void agotaLosReintentosYLanzaElUltimoError() {
        Falso t = new Falso();
        for (int i = 0; i < 4; i++) t.responde(502, "Bad gateway");
        FactuSmartException e = assertThrows(FactuSmartException.class, () -> cliente(t).rucs.listar());
        assertEquals(4, t.peticiones.size()); // 1 + maxReintentos (3)
        assertEquals(502, e.getStatus());
        assertEquals("Bad gateway", e.getRespuesta());
        assertNull(e.getCodigo());
        assertNull(e.getReintentable());
        assertTrue(e.getCampos().isEmpty());
    }

    @Test
    void timeoutYRedSeConviertenEnFactuSmartExceptionConStatus0() {
        Falso t = new Falso();
        for (int i = 0; i < 4; i++) t.falla(new HttpTimeoutException("timeout"));
        FactuSmartException e = assertThrows(FactuSmartException.class, () -> cliente(t).ping());
        assertEquals(0, e.getStatus());
        assertTrue(e.getMensaje().contains("no respondió"), e.getMensaje());
        assertEquals(4, t.peticiones.size());
    }

    @Test
    void operacionesQueNuncaSeReintentan() {
        String err = "{\"success\":false,\"message\":\"Error interno\",\"codigo\":\"ERROR_INTERNO\",\"reintentable\":true}";
        Falso t = new Falso();
        for (int i = 0; i < 5; i++) t.responde(503, err);
        FactuSmart fs = cliente(t);
        assertThrows(FactuSmartException.class, () -> fs.anulaciones.crear(Json.objeto("documentos", Json.lista())));
        assertThrows(FactuSmartException.class, () -> fs.guias.emitir(Json.objeto("serie_documento", "T001")));
        assertThrows(FactuSmartException.class, () -> fs.comprobantes.reenviar("abc"));
        assertThrows(FactuSmartException.class, () -> fs.comprobantes.consultarSunat("abc"));
        assertThrows(FactuSmartException.class, () -> fs.rucs.activar("20100070970", false));
        assertEquals(5, t.peticiones.size(), "cada operación debe hacer una sola petición");
        assertEquals("PATCH", t.peticiones.get(4).getMetodo());
        assertEquals("{\"activo\":false}", t.peticiones.get(4).getCuerpoTexto());
        assertEquals("20100070970", Json.leerObjeto(t.peticiones.get(2).getCuerpoTexto()).get("ruc"));
        Map<String, Object> guia = Json.leerObjeto(t.peticiones.get(1).getCuerpoTexto());
        assertEquals("#", guia.get("numero_documento"));
        assertNotNull(guia.get("hora_de_emision"));
    }

    @Test
    void reintentableTrueSeReintentaEnGetYEnCalcular() {
        String err = "{\"success\":false,\"message\":\"Error interno\",\"codigo\":\"ERROR_INTERNO\",\"reintentable\":true}";
        Falso t = new Falso().responde(500, err).responde(200, "{\"success\":true}")
                .responde(500, err).responde(200, "{\"success\":true}");
        FactuSmart fs = cliente(t);
        fs.comprobantes.historial("abc");
        fs.comprobantes.calcular(Json.lista(Json.objeto("descripcion", "x")), new Opciones().porcentajeIgv(10.5).ruc("20999999999"));
        assertEquals(4, t.peticiones.size());
        assertEquals("{\"items\":[{\"descripcion\":\"x\"}],\"porcentaje_igv\":10.5}", t.peticiones.get(3).getCuerpoTexto());
        assertEquals(BASE + "/calcular", t.peticiones.get(3).getUrl());
    }

    @Test
    void rucsNuncaRecibenElRucEmisor() {
        Falso t = new Falso().responde(200, "{}").responde(201, "{\"ya_existia\":false}");
        FactuSmart fs = cliente(t);
        fs.rucs.listar();
        fs.rucs.crear(Json.objeto("ruc", "20609999991", "empresa", "Mi Cliente SAC"));
        assertEquals(BASE + "/rucs", t.peticiones.get(0).getUrl());
        assertEquals("{\"ruc\":\"20609999991\",\"empresa\":\"Mi Cliente SAC\"}", t.peticiones.get(1).getCuerpoTexto());
    }

    @Test
    void erroresPorHttp() {
        Falso t = new Falso()
                .responde(401, "{\"success\":false,\"message\":\"API key inválida\",\"codigo\":\"NO_AUTENTICADO\",\"reintentable\":false}")
                .responde(402, "{\"message\":\"Sin créditos\",\"codigo\":\"SIN_CREDITOS\"}")
                .responde(403, "{\"message\":\"RUC no autorizado\"}")
                .responde(404, "{\"success\":false,\"message\":\"Comprobante no encontrado.\",\"codigo\":\"NO_ENCONTRADO\",\"reintentable\":false}")
                .responde(422, "{\"success\":false,\"message\":\"Datos inválidos\",\"codigo\":\"DATOS_INVALIDOS\",\"reintentable\":false,"
                        + "\"campos\":{\"items.0.cantidad\":[\"La cantidad es obligatoria.\"],\"serie\":\"Serie inválida\"}}")
                .responde(422, "{\"message\":{\"limite\":[\"El límite no puede ser mayor que 100.\"]}}")
                .responde(400, "");
        FactuSmart fs = cliente(t);

        ErrorDeAutenticacion a = assertThrows(ErrorDeAutenticacion.class, fs::ping);
        assertEquals("NO_AUTENTICADO", a.getCodigo());
        assertEquals("API key inválida", a.getMensaje());
        assertInstanceOf(Map.class, a.getRespuesta());
        assertThrows(SinCreditos.class, () -> fs.comprobantes.emitir(boleta()));
        RucNoAutorizado r = assertThrows(RucNoAutorizado.class, fs.cuenta::saldo);
        assertNull(r.getCodigo());
        NoEncontrado n = assertThrows(NoEncontrado.class, () -> fs.comprobantes.descargar("no-existe", "pdf"));
        assertEquals("NO_ENCONTRADO", n.getCodigo());
        assertEquals("Comprobante no encontrado.", n.getMensaje());
        ErrorDeValidacion v = assertThrows(ErrorDeValidacion.class, () -> fs.comprobantes.emitir(boleta()));
        assertEquals(Collections.singletonList("La cantidad es obligatoria."), v.getCampos().get("items.0.cantidad"));
        assertEquals(Collections.singletonList("Serie inválida"), v.getCampos().get("serie"));
        ErrorDeValidacion v2 = assertThrows(ErrorDeValidacion.class, () -> fs.comprobantes.listar(Json.objeto("limite", 500)));
        assertEquals("limite: El límite no puede ser mayor que 100.", v2.getMensaje());
        assertNotNull(v2.getErrores().get("limite"));
        FactuSmartException otro = assertThrows(FactuSmartException.class, fs.series::listar);
        assertEquals(FactuSmartException.class, otro.getClass());
        assertEquals("Error del API de FactuSmart. (HTTP 400)", otro.getMensaje());
        assertEquals(BASE + "/documents?limite=500&ruc=20100070970", t.peticiones.get(5).getUrl());
    }

    @Test
    void descargarDevuelveBytesYValidaElTipo() {
        byte[] pdf = "%PDF-1.4 bin\u00ff".getBytes(StandardCharsets.ISO_8859_1);
        Falso t = new Falso().responde(new RespuestaHttp(200, null, pdf));
        FactuSmart fs = cliente(t);
        assertArrayEquals(pdf, fs.comprobantes.descargar("a b/c", "pdf"));
        assertEquals(BASE + "/documents/a%20b%2Fc/pdf?ruc=20100070970", t.peticiones.get(0).getUrl());
        assertTrue(t.peticiones.get(0).getCabecera("Accept").startsWith("application/pdf"));
        assertThrows(FactuSmartException.class, () -> fs.comprobantes.descargar("abc", "zip"));
        assertThrows(FactuSmartException.class, () -> fs.comprobantes.consultar(" "));
        assertThrows(FactuSmartException.class, () -> fs.anulaciones.estado(null));
        assertThrows(FactuSmartException.class, () -> fs.comprobantes.calcular(Collections.emptyList()));
        assertEquals(1, t.peticiones.size());
    }

    // ───────────── 10. establecimientos (sin red) ─────────────

    @Test
    void establecimientosListarYCrear() {
        Falso t = new Falso().responde(200, "{\"success\":true,\"establecimientos\":[]}")
                .responde(200, "{\"success\":true,\"establecimientos\":[]}")
                .responde(201, "{\"success\":true,\"message\":\"ok\",\"establecimiento\":{}}")
                .responde(201, "{\"success\":true}");
        FactuSmart fs = cliente(t);
        Map<String, Object> r = fs.establecimientos.listar();
        assertTrue(r.containsKey("establecimientos"));
        fs.establecimientos.listar(Opciones.conRuc("20999999999"));
        Map<String, Object> datos = Json.objeto("codigo_anexo", "0001", "nombre", "Local Centro",
                "direccion", "Av. Siempre Viva 123", "ubigeo", "150101");
        fs.establecimientos.crear(datos);
        fs.establecimientos.crear(Json.objeto("codigo_anexo", "0002", "ruc", "20131312955"), Opciones.conRuc("20999999999"));

        assertEquals("GET", t.peticiones.get(0).getMetodo());
        assertEquals(BASE + "/establecimientos?ruc=20100070970", t.peticiones.get(0).getUrl());
        assertEquals(BASE + "/establecimientos?ruc=20999999999", t.peticiones.get(1).getUrl());
        assertEquals("POST", t.peticiones.get(2).getMetodo());
        assertEquals(BASE + "/establecimientos", t.peticiones.get(2).getUrl());
        assertEquals("{\"codigo_anexo\":\"0001\",\"nombre\":\"Local Centro\",\"direccion\":\"Av. Siempre Viva 123\","
                + "\"ubigeo\":\"150101\",\"ruc\":\"20100070970\"}", t.peticiones.get(2).getCuerpoTexto());
        assertEquals("20131312955", Json.leerObjeto(t.peticiones.get(3).getCuerpoTexto()).get("ruc"), "se respeta el ruc del usuario");
        assertFalse(datos.containsKey("ruc"), "no se modifica el Map del usuario");
        assertThrows(FactuSmartException.class, () -> fs.establecimientos.crear(null));
        assertEquals(4, t.peticiones.size());
    }

    @Test
    void establecimientosCrearNuncaSeReintentaYListarSi() {
        String err = "{\"success\":false,\"message\":\"Error interno\",\"codigo\":\"ERROR_INTERNO\",\"reintentable\":true}";
        Falso t = new Falso().responde(503, err).responde(503, err).responde(200, "{\"success\":true,\"establecimientos\":[]}");
        FactuSmart fs = cliente(t);
        FactuSmartException e = assertThrows(FactuSmartException.class,
                () -> fs.establecimientos.crear(Json.objeto("codigo_anexo", "0001")));
        assertEquals(503, e.getStatus());
        assertEquals(1, t.peticiones.size(), "crear: una sola petición ante 503");
        fs.establecimientos.listar();
        assertEquals(3, t.peticiones.size(), "listar es GET: se reintenta");

        Falso t2 = new Falso().responde(403, "{\"success\":false,\"message\":\"Tu plan no admite más locales.\","
                + "\"codigo\":\"LIMITE_ESTABLECIMIENTOS\",\"reintentable\":false}");
        RucNoAutorizado lim = assertThrows(RucNoAutorizado.class,
                () -> cliente(t2).establecimientos.crear(Json.objeto("codigo_anexo", "0009")));
        assertEquals("LIMITE_ESTABLECIMIENTOS", lim.getCodigo());
    }

    @Test
    void buscarProductoSunatRutaYQuery() {
        String err = "{\"success\":false,\"message\":\"Error interno\",\"codigo\":\"ERROR_INTERNO\",\"reintentable\":true}";
        Falso t = new Falso().responde(200, "{\"success\":true,\"catalogo_sunat\":\"25\",\"datos\":[]}")
                .responde(503, err).responde(200, "{\"success\":true,\"datos\":[]}");
        FactuSmart fs = cliente(t);
        Map<String, Object> r = fs.catalogos.buscarProductoSunat("computador");
        assertEquals("25", r.get("catalogo_sunat"));
        fs.catalogos.buscarProductoSunat("aceite de oliva", new Opciones().limite(5).ruc("20999999999"));
        assertEquals("GET", t.peticiones.get(0).getMetodo());
        assertEquals(BASE + "/catalogos/productos-sunat?q=computador&ruc=20100070970", t.peticiones.get(0).getUrl());
        assertEquals(t.peticiones.get(1).getUrl(), t.peticiones.get(2).getUrl(), "GET: se reintenta");
        assertEquals(BASE + "/catalogos/productos-sunat?q=aceite%20de%20oliva&limite=5&ruc=20999999999", t.peticiones.get(2).getUrl());
        assertThrows(FactuSmartException.class, () -> fs.catalogos.buscarProductoSunat(" "));
        assertEquals(3, t.peticiones.size());
    }

    @Test
    void buscarProductoSunatConNivel() {
        String ok = "{\"success\":true,\"datos\":[]}";
        Falso t = new Falso().responde(200, ok).responde(200, ok).responde(200, ok);
        FactuSmart fs = cliente(t);
        fs.catalogos.buscarProductoSunat("computador", new Opciones().nivel("producto"));
        fs.catalogos.buscarProductoSunat("43", new Opciones().limite(10).nivel("familia").ruc("20999999999"));
        fs.catalogos.buscarProductoSunat("computador", new Opciones().nivel(" "));
        assertEquals(BASE + "/catalogos/productos-sunat?q=computador&nivel=producto&ruc=20100070970", t.peticiones.get(0).getUrl());
        assertEquals(BASE + "/catalogos/productos-sunat?q=43&limite=10&nivel=familia&ruc=20999999999", t.peticiones.get(1).getUrl());
        assertEquals(BASE + "/catalogos/productos-sunat?q=computador&ruc=20100070970", t.peticiones.get(2).getUrl(), "sin nivel no se manda");
    }

    // ───────────── 11. descargar con formato (sin red) ─────────────

    @Test
    void descargarPdfConFormato() {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1);
        Falso t = new Falso().responde(new RespuestaHttp(200, null, pdf)).responde(new RespuestaHttp(200, null, pdf))
                .responde(new RespuestaHttp(200, null, pdf));
        FactuSmart fs = cliente(t);
        fs.comprobantes.descargar("abc", "pdf", new Opciones().formato("ticket"));
        fs.comprobantes.descargar("abc", "pdf", Opciones.conFormato("ticket58").ruc("20999999999"));
        fs.comprobantes.descargar("abc", "pdf", Opciones.conRuc("20999999999"));
        assertEquals("GET", t.peticiones.get(0).getMetodo());
        assertEquals(BASE + "/documents/abc/pdf?formato=ticket&ruc=20100070970", t.peticiones.get(0).getUrl());
        assertEquals(BASE + "/documents/abc/pdf?formato=ticket58&ruc=20999999999", t.peticiones.get(1).getUrl());
        assertEquals(BASE + "/documents/abc/pdf?ruc=20999999999", t.peticiones.get(2).getUrl(), "sin formato no se manda");
    }

    @Test
    void descargarFormatoInvalidoLanzaSinPeticion() {
        Falso t = new Falso();
        FactuSmart fs = cliente(t);
        ErrorDeValidacion x = assertThrows(ErrorDeValidacion.class,
                () -> fs.comprobantes.descargar("abc", "xml", Opciones.conFormato("ticket")));
        assertEquals(422, x.getStatus());
        assertEquals("DATOS_INVALIDOS", x.getCodigo());
        assertFalse(x.getCampos().get("formato").isEmpty());
        assertThrows(ErrorDeValidacion.class, () -> fs.comprobantes.descargar("abc", "cdr", Opciones.conFormato("a4")));
        ErrorDeValidacion v = assertThrows(ErrorDeValidacion.class,
                () -> fs.comprobantes.descargar("abc", "pdf", Opciones.conFormato("ticket70")));
        assertEquals(422, v.getStatus());
        assertEquals("DATOS_INVALIDOS", v.getCodigo());
        assertTrue(v.getCampos().containsKey("formato"));
        assertThrows(ErrorDeValidacion.class, () -> fs.comprobantes.descargar("abc", "pdf", Opciones.conFormato("TICKET")));
        assertEquals(0, t.peticiones.size(), "ninguna petición");
    }

    @Test
    void respuestaNoJsonEnExitoEsError() {
        Falso t = new Falso().responde(200, "<html>proxy</html>");
        FactuSmartException e = assertThrows(FactuSmartException.class, () -> cliente(t).ping());
        assertTrue(e.getMensaje().contains("no es JSON"));
    }

    @Test
    void leerSegundosDeEspera() {
        assertEquals(5L, FactuSmart.leerSegundosDeEspera("5", ""));
        assertEquals(12L, FactuSmart.leerSegundosDeEspera(null, "Demasiadas peticiones. Espera 12 segundos e intenta de nuevo."));
        assertEquals(0L, FactuSmart.leerSegundosDeEspera("Wed, 21 Oct 2015 07:28:00 GMT", ""));
        assertNull(FactuSmart.leerSegundosDeEspera(null, "nada"));
    }

    // ───────────── Consultas RUC / DNI ─────────────

    static final String RUC_OK = "{\"success\":true,\"tipo\":\"ruc\",\"numero\":\"20131312955\","
            + "\"nombre\":\"SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA - SUNAT\","
            + "\"direccion\":\"AV. GARCILASO DE LA VEGA - Nro: 1472  - LIMA\",\"estado\":null,\"condicion\":null,"
            + "\"ubigeo\":\"150101\",\"desde_cache\":false,\"consultas_restantes\":29999,\"periodo\":\"mes\","
            + "\"se_renueva\":\"2026-11-23\"}";

    @Test
    void consultasRucYDniSinRucEmisorYParseo() {
        Falso t = new Falso().responde(200, RUC_OK)
                .responde(200, "{\"success\":true,\"tipo\":\"dni\",\"numero\":\"12345678\",\"nombre\":\"JUAN CARLOS PEREZ GARCIA\","
                        + "\"direccion\":null,\"estado\":null,\"condicion\":null,\"ubigeo\":null,\"desde_cache\":true,"
                        + "\"consultas_restantes\":49,\"periodo\":\"dia\",\"se_renueva\":\"2026-11-07\"}")
                .responde(200, RUC_OK);
        FactuSmart fs = cliente(t); // el cliente tiene RUC por defecto: no debe viajar

        Map<String, Object> r = fs.consultas.ruc("20131312955");
        assertEquals("GET", t.peticiones.get(0).getMetodo());
        assertEquals(BASE + "/consultas/ruc/20131312955", t.peticiones.get(0).getUrl());
        assertEquals(Boolean.TRUE, r.get("success"));
        assertEquals("ruc", r.get("tipo"));
        assertEquals("20131312955", r.get("numero"));
        assertTrue(((String) r.get("nombre")).startsWith("SUPERINTENDENCIA"));
        assertEquals("150101", r.get("ubigeo"));
        assertTrue(r.containsKey("estado"));
        assertNull(r.get("estado"));
        assertNull(r.get("condicion"));
        assertEquals(Boolean.FALSE, r.get("desde_cache"));
        assertEquals(29999, ((Number) r.get("consultas_restantes")).intValue());
        assertEquals("mes", r.get("periodo"));
        assertEquals("2026-11-23", r.get("se_renueva"));

        Map<String, Object> d = fs.consultas.dni(" 12345678 ");
        assertEquals(BASE + "/consultas/dni/12345678", t.peticiones.get(1).getUrl());
        assertNull(d.get("direccion"));
        assertEquals(Boolean.TRUE, d.get("desde_cache"));
        assertEquals("dia", d.get("periodo"));

        fs.consultas.documento("RUC", "20131312955");
        assertEquals(BASE + "/consultas/ruc/20131312955", t.peticiones.get(2).getUrl());

        for (SolicitudHttp p : t.peticiones) {
            assertFalse(p.getUrl().contains("ruc="), "no lleva ?ruc=: " + p.getUrl());
            assertNull(p.getCabecera("X-RUC"));
            assertEquals("clave", p.getCabecera("X-API-Key"));
            assertEquals("factusmart-java/" + FactuSmart.VERSION, p.getCabecera("User-Agent"));
        }
    }

    @Test
    void consultasValidaElTipoSinPeticion() {
        Falso t = new Falso();
        FactuSmart fs = cliente(t);
        ErrorDeValidacion e = assertThrows(ErrorDeValidacion.class, () -> fs.consultas.documento("ce", "001234567"));
        assertEquals(422, e.getStatus());
        assertEquals("DATOS_INVALIDOS", e.getCodigo());
        assertEquals(Boolean.FALSE, e.getReintentable());
        assertTrue(e.getCampos().containsKey("tipo"));
        assertThrows(FactuSmartException.class, () -> fs.consultas.ruc(" "));
        assertThrows(FactuSmartException.class, () -> fs.consultas.dni(null));
        assertTrue(t.peticiones.isEmpty());
    }

    @Test
    void limiteConsultasNoSeReintenta() {
        Falso t = new Falso().responde(RespuestaHttp.de(429,
                "{\"success\":false,\"message\":\"Se acabó tu cupo de consultas DNI/RUC de este mes.\","
                        + "\"codigo\":\"LIMITE_CONSULTAS\",\"reintentable\":false,\"consultas_restantes\":0,"
                        + "\"limite_consultas\":30000,\"periodo\":\"mes\",\"se_renueva\":\"2026-11-23\"}"));
        LimiteDePeticiones e = assertThrows(LimiteDePeticiones.class, () -> cliente(t).consultas.ruc("20131312955"));
        assertEquals(1, t.peticiones.size());
        assertTrue(esperas.isEmpty());
        assertEquals(429, e.getStatus());
        assertEquals("LIMITE_CONSULTAS", e.getCodigo());
        assertFalse(e.isReintentable());
        assertEquals("2026-11-23", ((Map<?, ?>) e.getRespuesta()).get("se_renueva"));
    }

    @Test
    void consultasReintentaLimitePeticionesY503PeroNo404Ni422() {
        Falso t = new Falso()
                .responde(429, "{\"success\":false,\"message\":\"Demasiadas peticiones. Espera 2 segundos.\","
                        + "\"codigo\":\"LIMITE_PETICIONES\",\"reintentable\":true}")
                .responde(503, "{\"success\":false,\"message\":\"Ni SUNAT ni el respaldo respondieron.\","
                        + "\"codigo\":\"CONSULTA_NO_DISPONIBLE\",\"reintentable\":true}")
                .responde(200, RUC_OK)
                .responde(404, "{\"success\":false,\"message\":\"No se encontró el RUC.\",\"codigo\":\"NO_ENCONTRADO\",\"reintentable\":false}")
                .responde(422, "{\"success\":false,\"message\":\"RUC inválido.\",\"codigo\":\"DATOS_INVALIDOS\",\"reintentable\":false,"
                        + "\"campos\":{\"numero\":[\"El RUC no es válido.\"]}}");
        FactuSmart fs = cliente(t);
        assertEquals("ruc", fs.consultas.ruc("20131312955").get("tipo"));
        assertEquals(3, t.peticiones.size());
        assertEquals(Arrays.asList(2000L, 2000L), esperas);

        NoEncontrado nf = assertThrows(NoEncontrado.class, () -> fs.consultas.ruc("20000000001"));
        assertEquals("NO_ENCONTRADO", nf.getCodigo());
        ErrorDeValidacion ev = assertThrows(ErrorDeValidacion.class, () -> fs.consultas.ruc("12345678901"));
        assertEquals(Collections.singletonList("El RUC no es válido."), ev.getCampos().get("numero"));
        assertEquals(5, t.peticiones.size());
    }

    @Test
    void raizSinRuc() {
        Falso t = new Falso().responde(200, "{\"success\":true,\"api\":\"FactuSmart API de facturación\","
                + "\"docs\":\"https://factusmart.pe/api-facturacion-electronica/docs\",\"estado\":\"ok\"}");
        Map<String, Object> r = cliente(t).raiz();
        assertEquals("ok", r.get("estado"));
        assertEquals("GET", t.peticiones.get(0).getMetodo());
        assertEquals(BASE + "/", t.peticiones.get(0).getUrl());
    }

    // ───────────── Configuración del RUC ─────────────

    static final String CONFIG_OK = "{\"success\":true,\"ruc\":\"20777666555\",\"nombre\":\"API TENANT CLEAN SAC\","
            + "\"entorno\":\"produccion\",\"certificado\":{\"instalado\":true,\"vence\":\"2027-11-10\",\"dias_para_vencer\":364},"
            + "\"sol\":{\"configurado\":true,\"usuario\":\"FA*****1\"},\"envio\":\"sunat\",\"listo_para_produccion\":true,\"faltantes\":[]}";

    static void sinRucEmisor(SolicitudHttp p) {
        assertNull(p.getCabecera("X-RUC"), "no lleva X-RUC");
        assertFalse(p.getUrl().contains("?"), "no lleva query: " + p.getUrl());
        assertEquals("clave", p.getCabecera("X-API-Key"));
        assertEquals("factusmart-java/" + FactuSmart.VERSION, p.getCabecera("User-Agent"));
    }

    @Test
    void subirCertificadoDesdeArchivoYDesdeBytesEnBase64(@TempDir Path dir) throws IOException {
        byte[] pfx = new byte[300];
        for (int i = 0; i < pfx.length; i++) pfx[i] = (byte) (i * 7); // binario con bytes > 127 y ceros
        Path archivo = dir.resolve("mi certificado.pfx");
        Files.write(archivo, pfx);
        String resp = "{\"success\":true,\"ruc\":\"20777666555\",\"certificado\":{\"instalado\":true,"
                + "\"titular\":\"API TENANT CLEAN SAC\",\"vence\":\"2027-11-10\",\"dias_para_vencer\":364,\"ruc_coincide\":true}}";
        Falso t = new Falso().responde(200, resp).responde(200, resp).responde(200, resp);
        FactuSmart fs = cliente(t); // el cliente tiene RUC por defecto: no debe viajar

        Map<String, Object> r = fs.rucs.subirCertificado("20777666555", archivo, "Clave#Pfx1");
        Map<?, ?> cert = (Map<?, ?>) r.get("certificado");
        assertEquals(Boolean.TRUE, cert.get("instalado"));
        assertEquals(364, ((Number) cert.get("dias_para_vencer")).intValue());
        assertEquals(Boolean.TRUE, cert.get("ruc_coincide"));
        fs.rucs.subirCertificado("20777666555", archivo.toString(), "Clave#Pfx1");
        fs.rucs.subirCertificado(" 20777666555 ", pfx, "Clave#Pfx1");

        assertEquals(3, t.peticiones.size());
        String esperado = "{\"certificado\":\"" + Base64.getEncoder().encodeToString(pfx) + "\",\"clave\":\"Clave#Pfx1\"}";
        for (SolicitudHttp p : t.peticiones) {
            assertEquals("PUT", p.getMetodo());
            assertEquals(BASE + "/rucs/20777666555/certificado", p.getUrl());
            assertEquals("application/json", p.getCabecera("Content-Type"));
            assertEquals(esperado, p.getCuerpoTexto());
            sinRucEmisor(p);
        }
        Map<?, ?> enviado = (Map<?, ?>) Json.leer(t.peticiones.get(0).getCuerpoTexto());
        assertArrayEquals(pfx, Base64.getDecoder().decode((String) enviado.get("certificado")));
    }

    @Test
    void subirCertificadoValidaSinPeticionYSinMostrarLaClave(@TempDir Path dir) {
        Falso t = new Falso();
        FactuSmart fs = cliente(t);
        Path noExiste = dir.resolve("no-existe.pfx");
        FactuSmartException e = assertThrows(FactuSmartException.class,
                () -> fs.rucs.subirCertificado("20777666555", noExiste, "SecretoNoMostrar"));
        assertTrue(e.getMessage().contains("no existe el archivo"), e.getMessage());
        assertTrue(e.getMessage().contains("no-existe.pfx"), e.getMessage());
        assertFalse(e.getMessage().contains("SecretoNoMostrar"));
        assertFalse(e.toString().contains("SecretoNoMostrar"));
        assertThrows(FactuSmartException.class, () -> fs.rucs.subirCertificado("20777666555", dir, "x")); // carpeta
        assertThrows(FactuSmartException.class, () -> fs.rucs.subirCertificado(" ", new byte[] {1}, "x"));
        assertThrows(FactuSmartException.class, () -> fs.rucs.subirCertificado("20777666555", new byte[0], "x"));
        assertThrows(FactuSmartException.class, () -> fs.rucs.subirCertificado("20777666555", new byte[] {1}, ""));
        assertThrows(FactuSmartException.class, () -> fs.rucs.subirCertificado("20777666555", (String) null, "x"));
        assertTrue(t.peticiones.isEmpty());
    }

    @Test
    void errorDelApiNuncaMuestraLaClaveNiElCertificado() {
        byte[] pfx = "contenido-del-pfx-de-prueba".getBytes(StandardCharsets.UTF_8);
        String b64 = Base64.getEncoder().encodeToString(pfx);
        // Un API (o proxy) que repite lo recibido en el error: el SDK lo oculta igual.
        Falso t = new Falso()
                .responde(422, "{\"success\":false,\"message\":\"El certificado no abre con la clave Clave#Pfx1.\","
                        + "\"codigo\":\"DATOS_INVALIDOS\",\"reintentable\":false,"
                        + "\"campos\":{\"clave\":[\"Clave#Pfx1 no sirve\"],\"certificado\":[\"" + b64 + "\"]}}")
                .responde(422, "{\"success\":false,\"message\":\"clave_sol MiClaveSol9 inválida\",\"codigo\":\"DATOS_INVALIDOS\","
                        + "\"reintentable\":false,\"campos\":{\"clave_sol\":[\"MiClaveSol9\"]}}")
                .responde(500, "<html>error con MiClaveSol9</html>");
        FactuSmart fs = cliente(t);

        ErrorDeValidacion e = assertThrows(ErrorDeValidacion.class, () -> fs.rucs.subirCertificado("20777666555", pfx, "Clave#Pfx1"));
        assertEquals(1, t.peticiones.size()); // un 422 nunca se reintenta
        assertEquals("DATOS_INVALIDOS", e.getCodigo());
        for (String texto : Arrays.asList(e.getMessage(), e.toString(), e.getMensaje(), String.valueOf(e.getCampos()),
                String.valueOf(e.getErrores()), String.valueOf(e.getRespuesta()))) {
            assertFalse(texto.contains("Clave#Pfx1"), texto);
            assertFalse(texto.contains(b64), texto);
        }
        assertEquals("El certificado no abre con la clave ***.", e.getMensaje());
        assertEquals(Collections.singletonList("*** no sirve"), e.getCampos().get("clave"));

        ErrorDeValidacion s = assertThrows(ErrorDeValidacion.class,
                () -> fs.rucs.configurarSunat("20777666555", "FACTURA1", "MiClaveSol9", null));
        assertFalse(s.toString().contains("MiClaveSol9"));
        assertFalse(String.valueOf(s.getRespuesta()).contains("MiClaveSol9"));
        assertEquals("clave_sol *** inválida", s.getMensaje());

        FactuSmartException h = assertThrows(FactuSmartException.class,
                () -> fs.rucs.configurarSunat("20777666555", "FACTURA1", "MiClaveSol9", "produccion"));
        assertFalse(h.toString().contains("MiClaveSol9"));
        assertFalse(String.valueOf(h.getRespuesta()).contains("MiClaveSol9"));
        assertEquals(3, t.peticiones.size()); // los PUT no se reintentan ni ante 500
    }

    @Test
    void configurarSunatMandaSoloLoQueSePasa() {
        Falso t = new Falso().responde(200, CONFIG_OK).responde(200, CONFIG_OK).responde(200, CONFIG_OK);
        FactuSmart fs = cliente(t);

        Map<String, Object> r = fs.rucs.configurarSunat("20777666555", "FACTURA1", "ClaveSol9", null);
        assertEquals("FA*****1", ((Map<?, ?>) r.get("sol")).get("usuario"));
        assertEquals(Boolean.TRUE, r.get("listo_para_produccion"));
        assertEquals(Collections.emptyList(), r.get("faltantes"));
        fs.rucs.configurarSunat("20777666555", "produccion");
        fs.rucs.configurarSunat("20777666555", "20777666555FACTURA1", "ClaveSol9", "prueba");

        assertEquals("{\"usuario_sol\":\"FACTURA1\",\"clave_sol\":\"ClaveSol9\"}", t.peticiones.get(0).getCuerpoTexto());
        assertEquals("{\"entorno\":\"produccion\"}", t.peticiones.get(1).getCuerpoTexto());
        assertEquals("{\"usuario_sol\":\"20777666555FACTURA1\",\"clave_sol\":\"ClaveSol9\",\"entorno\":\"prueba\"}",
                t.peticiones.get(2).getCuerpoTexto());
        for (SolicitudHttp p : t.peticiones) {
            assertEquals("PUT", p.getMetodo());
            assertEquals(BASE + "/rucs/20777666555/sunat", p.getUrl());
            sinRucEmisor(p);
        }

        assertThrows(FactuSmartException.class, () -> fs.rucs.configurarSunat("20777666555", "FACTURA1", null, null));
        assertThrows(FactuSmartException.class, () -> fs.rucs.configurarSunat("20777666555", null, "ClaveSol9", "prueba"));
        assertThrows(FactuSmartException.class, () -> fs.rucs.configurarSunat("20777666555", null, null, null));
        assertThrows(FactuSmartException.class, () -> fs.rucs.configurarSunat("20777666555", " "));
        assertThrows(FactuSmartException.class, () -> fs.rucs.configurarSunat(null, "prueba"));
        assertEquals(3, t.peticiones.size());
    }

    @Test
    void verificarYConfiguracionSinCuerpoSinRucEmisorYConReintento() {
        Falso t = new Falso()
                .responde(503, "{\"success\":false,\"message\":\"Mantenimiento\",\"reintentable\":true}")
                .responde(200, "{\"success\":true,\"ruc\":\"20777666555\",\"listo_para_emitir\":false,\"entorno\":\"produccion\","
                        + "\"checks\":{\"certificado\":{\"ok\":true,\"mensaje\":\"Vigente\"},"
                        + "\"sol\":{\"ok\":false,\"mensaje\":\"SUNAT rechazó el usuario o la clave SOL.\"}}}")
                .responde(200, CONFIG_OK);
        FactuSmart fs = cliente(t);

        Map<String, Object> v = fs.rucs.verificar("20777666555");
        assertEquals(Boolean.FALSE, v.get("listo_para_emitir"));
        Map<?, ?> checks = (Map<?, ?>) v.get("checks");
        assertEquals(Boolean.FALSE, ((Map<?, ?>) checks.get("sol")).get("ok"));
        assertEquals(2, t.peticiones.size());
        assertEquals(1, esperas.size());
        for (int i = 0; i < 2; i++) {
            SolicitudHttp p = t.peticiones.get(i);
            assertEquals("POST", p.getMetodo());
            assertEquals(BASE + "/rucs/20777666555/verificar", p.getUrl());
            assertNull(p.getCuerpo());
            assertNull(p.getCabecera("Content-Type"));
            sinRucEmisor(p);
        }

        Map<String, Object> c = fs.rucs.configuracion("20777666555");
        assertEquals("produccion", c.get("entorno"));
        assertEquals("sunat", c.get("envio"));
        assertEquals("2027-11-10", ((Map<?, ?>) c.get("certificado")).get("vence"));
        SolicitudHttp g = t.peticiones.get(2);
        assertEquals("GET", g.getMetodo());
        assertEquals(BASE + "/rucs/20777666555/configuracion", g.getUrl());
        assertNull(g.getCuerpo());
        sinRucEmisor(g);

        assertThrows(FactuSmartException.class, () -> fs.rucs.verificar(""));
        assertThrows(FactuSmartException.class, () -> fs.rucs.configuracion(null));
        assertEquals(3, t.peticiones.size());
    }
}
