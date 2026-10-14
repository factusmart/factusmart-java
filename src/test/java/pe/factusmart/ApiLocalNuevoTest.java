package pe.factusmart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pruebas 10 y 11 contra el API LOCAL, sin emitir nada ni crear locales: se pueden correr aparte con
 * {@code ./gradlew test --tests pe.factusmart.ApiLocalNuevoTest}. {@code establecimientos.crear}
 * solo se prueba sin red (SinRedTest).
 */
@Tag("integracion")
@SuppressWarnings("unchecked")
class ApiLocalNuevoTest {

    static FactuSmart fs;

    @BeforeAll
    static void preparar() {
        if (ApiLocalTest.BASE_URL.toLowerCase().contains("factusmart.pe")) {
            throw new IllegalStateException("Estas pruebas no se corren contra producción.");
        }
        fs = ApiLocalTest.nuevoCliente(ApiLocalTest.API_KEY);
    }

    @Test
    void p10_establecimientosListar() {
        Map<String, Object> r = fs.establecimientos.listar();
        assertEquals(true, r.get("success"));
        assertInstanceOf(List.class, r.get("establecimientos"));
        System.out.println("  establecimientos: " + ((List<Object>) r.get("establecimientos")).size());
    }

    @Test
    void buscarProductoSunatComputador() {
        Map<String, Object> r = fs.catalogos.buscarProductoSunat("computador", new Opciones().limite(50));
        assertEquals(true, r.get("success"));
        List<Map<String, Object>> datos = (List<Map<String, Object>>) r.get("datos");
        assertTrue(datos.stream().anyMatch(x -> "43211503".equals(String.valueOf(x.get("codigo")))),
                "no vino 43211503: " + datos);
        System.out.println("  productos-sunat 'computador': " + datos.size() + " filas, total " + r.get("total"));
    }

    @Test
    void p11_pdfTicket58DeUnComprobanteExistente() {
        List<Map<String, Object>> l = (List<Map<String, Object>>) fs.comprobantes.listar(Json.objeto("limite", 1)).get("comprobantes");
        Assumptions.assumeFalse(l.isEmpty(), "no hay comprobantes en el RUC de prueba");
        String id = (String) l.get(0).get("external_id");
        byte[] pdf = fs.comprobantes.descargar(id, "pdf", Opciones.conFormato("ticket58"));
        assertTrue(pdf.length > 4);
        assertEquals("%PDF", new String(Arrays.copyOf(pdf, 4), StandardCharsets.ISO_8859_1));
        byte[] a4 = fs.comprobantes.descargar(id, "pdf");
        assertFalse(Arrays.equals(a4, pdf), "el ticket debería ser distinto del A4");
        System.out.println("  " + id + " ticket58=" + pdf.length + " bytes, A4=" + a4.length + " bytes");
    }
}
