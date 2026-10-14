package pe.factusmart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Prueba 8 (contrato): cada operationId del openapi.yaml oficial debe tener su método en el SDK. Si el
 * API crece y nadie actualiza el SDK, esta prueba falla. Ruta del yaml: variable FACTUSMART_OPENAPI o
 * la copia en tests/fixtures/openapi.yaml.
 */
class ContratoTest {

    /** operationId → método del SDK (DISENO_SDK.md). */
    static final Map<String, String> METODOS = new LinkedHashMap<>();

    static {
        METODOS.put("ping", "ping");
        METODOS.put("getAccount", "cuenta.saldo");
        METODOS.put("listSeries", "series.listar");
        METODOS.put("emitDocument", "comprobantes.emitir");
        METODOS.put("listDocuments", "comprobantes.listar");
        METODOS.put("getDocument", "comprobantes.consultar");
        METODOS.put("downloadDocumentFile", "comprobantes.descargar");
        METODOS.put("resendDocument", "comprobantes.reenviar");
        METODOS.put("querySunatStatus", "comprobantes.consultarSunat");
        METODOS.put("getDocumentHistory", "comprobantes.historial");
        METODOS.put("voidDocument", "anulaciones.crear");
        METODOS.put("getVoidStatus", "anulaciones.estado");
        METODOS.put("emitDispatchGuide", "guias.emitir");
        METODOS.put("getDispatchGuideStatus", "guias.estado");
        METODOS.put("listRucs", "rucs.listar");
        METODOS.put("createRuc", "rucs.crear");
        METODOS.put("setRucActivation", "rucs.activar");
        METODOS.put("calculateDocument", "comprobantes.calcular");
        METODOS.put("listCatalogs", "catalogos.listar");
        METODOS.put("getCatalog", "catalogos.consultar");
        // Webhook (sección `webhooks` del yaml): lo manda el API; el SDK solo verifica la firma (método estático).
        METODOS.put("webhookComprobanteEstado", "FactuSmart.verificarFirmaWebhook");
    }

    static Path rutaYaml() {
        String env = System.getenv("FACTUSMART_OPENAPI");
        if (env != null && !env.isEmpty()) return Paths.get(env);
        return Paths.get(System.getProperty("factusmart.raiz", "."), "tests", "fixtures", "openapi.yaml");
    }

    static boolean tieneMetodoPublico(Class<?> c, String nombre, boolean estatico) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(nombre) && Modifier.isStatic(m.getModifiers()) == estatico) return true;
        }
        return false;
    }

    @Test
    void todosLosOperationIdTienenMetodo() throws IOException {
        Path yaml = rutaYaml();
        assertTrue(Files.exists(yaml), "no existe " + yaml);
        String texto = new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^\\s*operationId:\\s*['\"]?([A-Za-z0-9_]+)['\"]?\\s*$").matcher(texto);
        List<String> ids = new ArrayList<>();
        while (m.find()) ids.add(m.group(1));
        assertFalse(ids.isEmpty(), "no se encontró ningún operationId en el yaml");

        List<String> faltan = new ArrayList<>();
        for (String id : ids) {
            String ruta = METODOS.get(id);
            if (ruta == null) {
                faltan.add(id + " (sin método asignado en el SDK)");
                continue;
            }
            String[] partes = ruta.split("\\.");
            boolean existe;
            if (partes[0].equals("FactuSmart")) {
                existe = tieneMetodoPublico(FactuSmart.class, partes[1], true);
            } else if (partes.length == 1) {
                existe = tieneMetodoPublico(FactuSmart.class, partes[0], false);
            } else {
                try {
                    Field f = FactuSmart.class.getField(partes[0]);
                    existe = Modifier.isPublic(f.getModifiers()) && tieneMetodoPublico(f.getType(), partes[1], false);
                } catch (NoSuchFieldException e) {
                    existe = false;
                }
            }
            if (!existe) faltan.add(id + " → " + ruta + " (no existe)");
        }
        assertEquals(new ArrayList<String>(), faltan, "operationId sin método en el SDK:\n  " + String.join("\n  ", faltan));

        List<String> sobran = new ArrayList<>();
        for (String id : METODOS.keySet()) if (!ids.contains(id)) sobran.add(id);
        assertEquals(new ArrayList<String>(), sobran, "métodos del SDK cuyo operationId ya no está en el yaml");
        System.out.println("  " + ids.size() + " operationId verificados contra " + yaml.toAbsolutePath().normalize());
    }
}
