package com.example.conexionfarmaco;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.util.List;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        DBHelper dbHelper = new DBHelper(context);
        List<JSONObject> pendientes = dbHelper.obtenerPendientes();

        if (pendientes.isEmpty()) return Result.success();

        Log.d("SyncWorker", "Iniciando sincronización de " + pendientes.size() + " elementos");

        boolean allSuccess = true;
        for (JSONObject p : pendientes) {
            if (!Utilidades.hayInternet(context)) return Result.retry();

            String id_local = "";
            try {
                id_local = p.getString("id_local");
                String url = p.getString("url");
                String metodo = p.getString("metodo");
                String json = p.getString("json");
                String tipo = p.getString("tipo");

                if (tipo.equals("couchdb")) {
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(json, metodo, url).get();
                    
                    if (res != null) {
                        JSONObject resJson = new JSONObject(res);
                        
                        boolean esExitoDirecto = resJson.optBoolean("ok", false) || res.contains("201") || res.contains("200");
                        boolean esRecursoInexistente = res.contains("404") || res.contains("not_found");
                        boolean esConflicto = res.contains("409") || res.contains("conflict");

                        if (esExitoDirecto || esRecursoInexistente) {
                            procesarExito(dbHelper, url, metodo, json, resJson);
                            dbHelper.eliminarPendiente(id_local);
                        } else if (esConflicto) {
                            Log.d("SyncWorker", "Conflicto detectado en " + metodo + ", intentando resolver...");
                            String cleanUrl = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
                            String getRes = new TareaServidor().execute("", "GET", cleanUrl).get();
                            
                            if (getRes != null && getRes.contains("_rev")) {
                                JSONObject getJson = new JSONObject(getRes);
                                String currentRev = getJson.getString("_rev");
                                
                                if (metodo.equals("DELETE")) {
                                    String retryUrl = cleanUrl + "?rev=" + currentRev;
                                    String deleteRes = new TareaServidor().execute("", "DELETE", retryUrl).get();
                                    if (deleteRes.contains("\"ok\":true") || deleteRes.contains("200")) {
                                        dbHelper.eliminarPendiente(id_local);
                                    } else { allSuccess = false; }
                                } else if (metodo.equals("PUT")) {
                                    // Para un PUT, necesitamos inyectar el nuevo _rev en el JSON
                                    JSONObject localObj = new JSONObject(json);
                                    localObj.put("_rev", currentRev);
                                    String retryRes = new TareaServidor().execute(localObj.toString(), "PUT", cleanUrl).get();
                                    if (retryRes != null && (retryRes.contains("\"ok\":true") || retryRes.contains("201"))) {
                                        procesarExito(dbHelper, cleanUrl, "PUT", localObj.toString(), new JSONObject(retryRes));
                                        dbHelper.eliminarPendiente(id_local);
                                    } else { allSuccess = false; }
                                }
                            } else if (getRes != null && getRes.contains("404")) {
                                dbHelper.eliminarPendiente(id_local);
                            } else {
                                allSuccess = false;
                            }
                        } else {
                            Log.e("SyncWorker", "Error en tarea: " + res);
                            allSuccess = false;
                            // Si es un error de red o de servidor, paramos el loop para no esperar timeouts innecesarios
                            if (res.contains("Error de red")) return Result.retry();
                        }
                    } else { 
                        allSuccess = false;
                        return Result.retry();
                    }
                } else if (tipo.equals("email")) {
                    enviarEmail(dbHelper, id_local, json);
                }
            } catch (Exception e) {
                Log.e("SyncWorker", "Fallo crítico: " + e.getMessage());
                allSuccess = false;
            }
        }
        return allSuccess ? Result.success() : Result.retry();
    }

    private void procesarExito(DBHelper dbHelper, String url, String metodo, String json, JSONObject resJson) throws Exception {
        if (resJson.has("rev") && !json.isEmpty() && (metodo.equals("POST") || metodo.equals("PUT"))) {
            JSONObject localObj = new JSONObject(json);
            String id_couch = localObj.optString("_id", "");
            String nuevo_rev = resJson.getString("rev");
            localObj.put("_rev", nuevo_rev);
            
            if (url.contains("medicamentos")) {
                dbHelper.guardarMedicamentoCache(localObj, true);
            } else if (url.contains("pedidos")) {
                dbHelper.guardarPedidoLocal(localObj);
            } else if (url.contains("farmacias")) {
                dbHelper.guardarFarmaciaCache(localObj, true);
            } else if (url.contains("usuarios")) {
                String[] datos = {
                    id_couch, nuevo_rev, localObj.optString("nombres"), localObj.optString("apellidos"),
                    localObj.optString("telefono"), localObj.optString("correo"), localObj.optString("clave"),
                    localObj.optString("direccion"), localObj.optString("alergias"), localObj.optString("tipo_sangre"),
                    localObj.optString("enfermedades"), localObj.optString("foto")
                };
                dbHelper.administrarUsuarios("nuevo", datos);
            }

            if (!id_couch.isEmpty()) {
                dbHelper.actualizarRevEnPendientes(id_couch, nuevo_rev);
            }
        }
    }

    private void enviarEmail(DBHelper dbHelper, String id_local, String json) throws Exception {
        JSONObject emailData = new JSONObject(json);
        MailManager mail = new MailManager(
                emailData.getString("destinatario"),
                emailData.getString("asunto"),
                emailData.getString("contenido")
        );
        mail.enviarSincrono();
        dbHelper.eliminarPendiente(id_local);
    }
}
