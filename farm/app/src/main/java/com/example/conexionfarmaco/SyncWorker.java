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

        boolean allSuccess = true;
        for (JSONObject p : pendientes) {
            try {
                String id_local = p.getString("id_local");
                String url = p.getString("url");
                String metodo = p.getString("metodo");
                String json = p.getString("json");
                String tipo = p.getString("tipo");

                if (tipo.equals("couchdb")) {
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(json, metodo, url).get();
                    if (res != null) {
                        JSONObject resJson = new JSONObject(res);
                        if (resJson.optBoolean("ok", false) || res.contains("409") || res.contains("201") || res.contains("200")) {
                            dbHelper.eliminarPendiente(id_local);
                        } else {
                            allSuccess = false;
                        }
                    } else {
                        allSuccess = false;
                    }
                } else if (tipo.equals("email")) {
                    JSONObject emailData = new JSONObject(json);
                    MailManager mail = new MailManager(
                            emailData.getString("destinatario"),
                            emailData.getString("asunto"),
                            emailData.getString("contenido")
                    );
                    mail.execute();
                    dbHelper.eliminarPendiente(id_local);
                }
            } catch (Exception e) {
                Log.e("SyncWorker", "Error: " + e.getMessage());
                allSuccess = false;
            }
        }

        return allSuccess ? Result.success() : Result.retry();
    }
}
