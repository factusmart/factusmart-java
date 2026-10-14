package pe.factusmart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** Caso común de DISENO_SDK.md. {@code ./gradlew casoComun} escribe {@code resultado_caso_comun.json}. */
public final class CasoComun {

    private CasoComun() {}

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> entrada() {
        return (List<Map<String, Object>>) (List<?>) Json.lista(
                Json.objeto("descripcion", "A", "cantidad", 3, "valor_unitario", 10.555, "afectacion", "10"),
                Json.objeto("descripcion", "B", "cantidad", 2, "valor_unitario", 5, "afectacion", "20"),
                Json.objeto("descripcion", "C", "cantidad", 1, "valor_unitario", 7.1, "afectacion", "30"),
                Json.objeto("descripcion", "D", "cantidad", 1, "valor_unitario", 100, "afectacion", "40"));
    }

    /** Mismo formato que {@code JSON.stringify(r, null, 2) + "\n"} del SDK de Node. */
    static String resultadoComoTexto() {
        return Json.aTextoBonito(FactuSmart.calcularComprobante(entrada())) + "\n";
    }

    public static void main(String[] args) throws IOException {
        String ruta = args.length > 0 ? args[0] : "resultado_caso_comun.json";
        Files.write(Paths.get(ruta), resultadoComoTexto().getBytes(StandardCharsets.UTF_8));
        System.out.println("Escrito " + ruta);
    }
}
