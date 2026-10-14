package pe.factusmart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cálculo local de IGV y totales (sin red), igual que la función de la doc pública (sección "Calcular
 * IGV y totales") y que los SDK de Node, PHP y Python: 2 decimales por ítem, "mitad hacia arriba"
 * ({@link RoundingMode#HALF_UP}) sobre {@link BigDecimal}, así que {@code 10.555 × 3} da {@code 31.67}.
 * Se usa con {@link FactuSmart#calcularComprobante(List)}.
 */
final class Calculo {

    private static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal UNO_MAS_IGV = BigDecimal.ONE.add(TASA_IGV);
    private static final BigDecimal DIECIOCHO = new BigDecimal("18");

    private Calculo() {}

    /** Redondea a 2 decimales, mitad hacia arriba, sin ceros sobrantes ({@code 5.70 → 5.7}, {@code 5.00 → 5}). */
    static BigDecimal redondear2(BigDecimal n) {
        return compacto(n.setScale(2, RoundingMode.HALF_UP));
    }

    /** Quita ceros sobrantes sin notación científica ({@code 100.00 → 100}, no {@code 1E+2}). */
    static BigDecimal compacto(BigDecimal n) {
        if (n.signum() == 0) return BigDecimal.ZERO;
        BigDecimal r = n.stripTrailingZeros();
        return r.scale() < 0 ? r.setScale(0) : r;
    }

    /** Convierte a BigDecimal sin el error binario de {@code double} ({@code 10.555 → 10.555} exacto). */
    static BigDecimal aDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(Json.numeroATexto((Number) v));
        if (v instanceof String) {
            try {
                return new BigDecimal(((String) v).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static Map<String, Object> calcular(List<?> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("calcularComprobante: pasa una lista con al menos un ítem.");
        }
        BigDecimal gravadas = BigDecimal.ZERO;
        BigDecimal exoneradas = BigDecimal.ZERO;
        BigDecimal inafectas = BigDecimal.ZERO;
        BigDecimal exportacion = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;

        List<Object> itemsApi = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Object elemento = items.get(i);
            int n = i + 1;
            if (!(elemento instanceof Map)) throw new IllegalArgumentException("calcularComprobante: ítem " + n + " debe ser un Map.");
            Map<?, ?> it = (Map<?, ?>) elemento;
            String afectacion = String.valueOf(it.get("afectacion"));
            if (!afectacion.equals("10") && !afectacion.equals("20") && !afectacion.equals("30") && !afectacion.equals("40")) {
                throw new IllegalArgumentException("calcularComprobante: ítem " + n + ", afectación \"" + it.get("afectacion")
                        + "\" no soportada (usa 10, 20, 30 o 40).");
            }
            BigDecimal cantidad = aDecimal(it.get("cantidad"));
            BigDecimal valorUnitario = aDecimal(it.get("valor_unitario"));
            if (cantidad == null || cantidad.signum() <= 0) {
                throw new IllegalArgumentException("calcularComprobante: ítem " + n + ", cantidad inválida.");
            }
            if (valorUnitario == null || valorUnitario.signum() < 0) {
                throw new IllegalArgumentException("calcularComprobante: ítem " + n + ", valor_unitario inválido.");
            }

            boolean gravado = afectacion.equals("10");
            BigDecimal base = redondear2(valorUnitario.multiply(cantidad));
            BigDecimal igv = gravado ? redondear2(base.multiply(TASA_IGV)) : BigDecimal.ZERO;

            switch (afectacion) {
                case "10": gravadas = gravadas.add(base); break;
                case "20": exoneradas = exoneradas.add(base); break;
                case "30": inafectas = inafectas.add(base); break;
                default: exportacion = exportacion.add(base);
            }
            totalIgv = totalIgv.add(igv);

            Map<String, Object> item = new LinkedHashMap<>();
            // Sin codigo_interno se omite la clave (igual que los SDK de Node, PHP y Python).
            if (it.get("codigo_interno") != null) item.put("codigo_interno", it.get("codigo_interno"));
            item.put("descripcion", it.get("descripcion"));
            Object unidad = it.get("unidad_de_medida");
            item.put("unidad_de_medida", unidad == null || "".equals(unidad) ? "NIU" : unidad);
            item.put("cantidad", compacto(cantidad));
            item.put("valor_unitario", redondear2(valorUnitario));
            item.put("codigo_tipo_precio", "01");
            item.put("precio_unitario", redondear2(gravado ? valorUnitario.multiply(UNO_MAS_IGV) : valorUnitario));
            item.put("codigo_tipo_afectacion_igv", afectacion);
            item.put("total_base_igv", base);
            item.put("porcentaje_igv", gravado ? DIECIOCHO : BigDecimal.ZERO);
            item.put("total_igv", igv);
            item.put("total_impuestos", igv);
            item.put("total_valor_item", base);
            item.put("total_item", redondear2(base.add(igv)));
            itemsApi.add(item);
        }

        BigDecimal totalValor = redondear2(gravadas.add(exoneradas).add(inafectas).add(exportacion));
        BigDecimal igv = redondear2(totalIgv);
        Map<String, Object> totales = new LinkedHashMap<>();
        totales.put("total_operaciones_gravadas", redondear2(gravadas));
        totales.put("total_operaciones_exoneradas", redondear2(exoneradas));
        totales.put("total_operaciones_inafectas", redondear2(inafectas));
        totales.put("total_exportacion", redondear2(exportacion));
        totales.put("total_igv", igv);
        totales.put("total_impuestos", igv);
        totales.put("total_valor", totalValor);
        totales.put("total_venta", redondear2(totalValor.add(igv)));

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("items", itemsApi);
        salida.put("totales", totales);
        return salida;
    }
}
