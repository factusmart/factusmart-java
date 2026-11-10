package pe.factusmart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RUCs de la cuenta. Se usa como {@code fs.rucs}. Estos endpoints nunca reciben el RUC emisor. */
public final class Rucs {

    private final FactuSmart fs;

    Rucs(FactuSmart fs) {
        this.fs = fs;
    }

    /** {@code GET /rucs}. */
    public Map<String, Object> listar() {
        return fs.objeto(FactuSmart.Peticion.get("/rucs").reintentable(true));
    }

    /**
     * {@code POST /rucs} — datos: {@code ruc}, {@code empresa}, {@code correo}, {@code clave}.
     * Idempotente por RUC (si ya es tuyo devuelve {@code ya_existia: true}), por eso se reintenta.
     */
    public Map<String, Object> crear(Map<String, ?> datos) {
        if (datos == null) throw new FactuSmartException("rucs.crear(datos): pasa ruc, empresa, correo y clave.");
        return fs.objeto(FactuSmart.Peticion.post("/rucs", new LinkedHashMap<String, Object>(datos)).reintentable(true));
    }

    /** {@code PATCH /rucs/{ruc}/activation} — activa o desactiva un RUC. No se reintenta automáticamente. */
    public Map<String, Object> activar(String ruc, boolean activo) {
        String r = FactuSmart.segmento(ruc, "rucs.activar(ruc, activo): falta el RUC.");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("activo", activo);
        return fs.objeto(FactuSmart.Peticion.patch("/rucs/" + r + "/activation", cuerpo).reintentable(false));
    }

    // ───────────── Configuración del RUC (certificado, SOL, entorno) ─────────────
    // El RUC va en la ruta: nunca se manda X-RUC ni ?ruc=. Las claves y el certificado nunca
    // aparecen en el mensaje de una excepción (se ocultan con ***).

    /**
     * {@code PUT /rucs/{ruc}/certificado} — instala el certificado digital (.pfx / .p12) leyendo el
     * archivo. El SDK lo manda en base64. El API comprueba que abra con la clave, que esté vigente y que
     * sea de este RUC. No se reintenta automáticamente.
     */
    public Map<String, Object> subirCertificado(String ruc, Path archivo, String clave) {
        if (archivo == null) throw new FactuSmartException("rucs.subirCertificado(ruc, archivo, clave): falta el archivo .pfx / .p12.");
        if (!Files.isRegularFile(archivo)) {
            throw new FactuSmartException("rucs.subirCertificado: no existe el archivo del certificado: " + archivo.toAbsolutePath());
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new FactuSmartException("rucs.subirCertificado: no se pudo leer el archivo del certificado: "
                    + archivo.toAbsolutePath(), 0, null, e);
        }
        return subirCertificado(ruc, bytes, clave);
    }

    /** Igual que {@link #subirCertificado(String, Path, String)}, con la ruta del archivo como texto. */
    public Map<String, Object> subirCertificado(String ruc, String rutaArchivo, String clave) {
        if (rutaArchivo == null || rutaArchivo.trim().isEmpty()) {
            throw new FactuSmartException("rucs.subirCertificado(ruc, archivo, clave): falta el archivo .pfx / .p12.");
        }
        return subirCertificado(ruc, Paths.get(rutaArchivo.trim()), clave);
    }

    /** Igual que {@link #subirCertificado(String, Path, String)}, con el contenido del .pfx en bytes. */
    public Map<String, Object> subirCertificado(String ruc, byte[] certificado, String clave) {
        String r = FactuSmart.segmento(ruc, "rucs.subirCertificado(ruc, archivo, clave): falta el RUC.");
        if (certificado == null || certificado.length == 0) {
            throw new FactuSmartException("rucs.subirCertificado: el certificado está vacío.");
        }
        if (clave == null || clave.isEmpty()) {
            throw new FactuSmartException("rucs.subirCertificado: falta la clave del certificado.");
        }
        String base64 = Base64.getEncoder().encodeToString(certificado);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("certificado", base64);
        cuerpo.put("clave", clave);
        return fs.objeto(FactuSmart.Peticion.put("/rucs/" + r + "/certificado", cuerpo)
                .secreto(clave).secreto(base64).reintentable(false));
    }

    /**
     * {@code PUT /rucs/{ruc}/sunat} — guarda el usuario secundario SOL y su clave y/o cambia el entorno
     * ({@code prueba} | {@code produccion}; {@code demo} se acepta como sinónimo de {@code prueba}). Solo se
     * mandan los valores no nulos; {@code usuarioSol} y {@code claveSol} van juntos. No llama a SUNAT: para
     * comprobar el SOL usa {@link #verificar(String)}. Para Smart PSE usa
     * {@link #configurarSmartPse(String, String, String, String)} o {@link #configurarSunat(String, Map)}.
     * No se reintenta automáticamente.
     */
    public Map<String, Object> configurarSunat(String ruc, String usuarioSol, String claveSol, String entorno) {
        String r = FactuSmart.segmento(ruc, "rucs.configurarSunat(ruc, usuarioSol, claveSol, entorno): falta el RUC.");
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("usuario_sol", usuarioSol);
        datos.put("clave_sol", claveSol);
        datos.put("entorno", entorno);
        return enviarSunat(r, datos);
    }

    /** {@code PUT /rucs/{ruc}/sunat} solo con el entorno ({@code prueba} | {@code produccion}). */
    public Map<String, Object> configurarSunat(String ruc, String entorno) {
        if (entorno == null || entorno.trim().isEmpty()) {
            throw new FactuSmartException("rucs.configurarSunat(ruc, entorno): falta el entorno (prueba | produccion).");
        }
        return configurarSunat(ruc, null, null, entorno);
    }

    /**
     * {@code PUT /rucs/{ruc}/sunat} con cualquier combinación de campos: {@code usuario_sol} +
     * {@code clave_sol}, {@code entorno} ({@code prueba} | {@code produccion} | {@code demo}),
     * {@code modalidad} ({@code propio} = tu certificado, directo a SUNAT · {@code pse} = Smart PSE firma y
     * envía) y {@code pse_usuario} + {@code pse_clave} (credenciales CPE de la empresa en Smart PSE). Los
     * valores nulos no se mandan. Las claves ({@code clave_sol}, {@code pse_clave}) se ocultan con
     * {@code ***} en cualquier error.
     *
     * <p>La respuesta es la misma de {@link #configuracion(String)} más {@code aviso} (texto, opcional):
     * {@code modalidad} ({@code propio} | {@code pse}), {@code pse} ({@code null} salvo con Smart PSE:
     * {@code proveedor}, {@code configurado}, {@code usuario} enmascarado) y, con Smart PSE, el aviso de
     * que el contribuyente debe <b>designar a Smart PSE como su PSE en SUNAT</b> con su Clave SOL.
     * No se reintenta automáticamente.
     */
    public Map<String, Object> configurarSunat(String ruc, Map<String, ?> datos) {
        String r = FactuSmart.segmento(ruc, "rucs.configurarSunat(ruc, datos): falta el RUC.");
        if (datos == null) {
            throw new FactuSmartException("rucs.configurarSunat(ruc, datos): pasa usuario_sol y clave_sol, entorno, "
                    + "modalidad o pse_usuario y pse_clave.");
        }
        return enviarSunat(r, new LinkedHashMap<String, Object>(datos));
    }

    /**
     * Atajo de {@code PUT /rucs/{ruc}/sunat} con {@code modalidad: pse}: Smart PSE firma y envía por ti
     * (sin certificado propio). Antes da de alta la empresa en Smart PSE; {@code pseUsuario} y
     * {@code pseClave} son sus credenciales CPE. {@code entorno} es opcional ({@code null} = no cambia).
     * Revisa el {@code aviso} de la respuesta y luego {@link #verificar(String)} ({@code checks.pse}).
     */
    public Map<String, Object> configurarSmartPse(String ruc, String pseUsuario, String pseClave, String entorno) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("modalidad", "pse");
        datos.put("pse_usuario", pseUsuario);
        datos.put("pse_clave", pseClave);
        datos.put("entorno", entorno);
        return configurarSunat(ruc, datos);
    }

    private static final List<String> ENTORNOS = Arrays.asList("prueba", "produccion", "demo");
    private static final List<String> MODALIDADES = Arrays.asList("propio", "pse");
    private static final List<String> CLAVES = Arrays.asList("clave_sol", "pse_clave");

    /** Valida y manda el PUT /sunat. Quita nulos y textos vacíos; las claves se mandan tal cual. */
    private Map<String, Object> enviarSunat(String r, Map<String, Object> datos) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : datos.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            if (v instanceof String) {
                String texto = CLAVES.contains(e.getKey()) ? (String) v : ((String) v).trim();
                if (texto.isEmpty()) continue;
                v = texto;
            }
            cuerpo.put(e.getKey(), v);
        }
        if (cuerpo.isEmpty()) {
            throw new FactuSmartException("rucs.configurarSunat: no hay nada que guardar. Pasa usuarioSol y claveSol, "
                    + "entorno (prueba | produccion), modalidad (propio | pse) o pse_usuario y pse_clave.");
        }
        if (cuerpo.containsKey("usuario_sol") != cuerpo.containsKey("clave_sol")) {
            throw new FactuSmartException("rucs.configurarSunat: usuarioSol y claveSol (usuario_sol y clave_sol) van juntos "
                    + "(pasa los dos o ninguno).");
        }
        if (cuerpo.containsKey("pse_usuario") != cuerpo.containsKey("pse_clave")) {
            throw new FactuSmartException("rucs.configurarSunat: pse_usuario y pse_clave van juntos (pasa los dos o ninguno).");
        }
        Object entorno = cuerpo.get("entorno");
        if (entorno != null && !ENTORNOS.contains(entorno)) {
            throw new FactuSmartException("rucs.configurarSunat: entorno inválido «" + entorno + "» (prueba | produccion).");
        }
        Object modalidad = cuerpo.get("modalidad");
        if (modalidad != null && !MODALIDADES.contains(modalidad)) {
            throw new FactuSmartException("rucs.configurarSunat: modalidad inválida «" + modalidad + "» (propio | pse).");
        }
        FactuSmart.Peticion p = FactuSmart.Peticion.put("/rucs/" + r + "/sunat", cuerpo);
        for (String k : CLAVES) {
            Object v = cuerpo.get(k);
            if (v != null) p.secreto(String.valueOf(v));
        }
        return fs.objeto(p.reintentable(false));
    }

    /**
     * {@code POST /rucs/{ruc}/verificar} — prueba real del certificado y del usuario SOL contra SUNAT,
     * sin emitir nada ({@code listo_para_emitir}, {@code modalidad}, {@code checks.certificado},
     * {@code checks.sol} y, con Smart PSE, {@code checks.pse}: token de prueba con las credenciales PSE
     * guardadas; {@code ok: false} = rechazadas o faltan, {@code ok: null} = Smart PSE no respondió). No
     * consume crédito.
     */
    public Map<String, Object> verificar(String ruc) {
        String r = FactuSmart.segmento(ruc, "rucs.verificar(ruc): falta el RUC.");
        return fs.objeto(FactuSmart.Peticion.post("/rucs/" + r + "/verificar", null).reintentable(true));
    }

    /**
     * {@code GET /rucs/{ruc}/configuracion} — entorno, certificado (vencimiento), usuario SOL
     * enmascarado, canal de envío, {@code modalidad} ({@code propio} | {@code pse}), {@code pse}
     * ({@code null} salvo con Smart PSE) y qué falta para producción ({@code faltantes}). No llama a SUNAT.
     */
    public Map<String, Object> configuracion(String ruc) {
        String r = FactuSmart.segmento(ruc, "rucs.configuracion(ruc): falta el RUC.");
        return fs.objeto(FactuSmart.Peticion.get("/rucs/" + r + "/configuracion").reintentable(true));
    }
}
