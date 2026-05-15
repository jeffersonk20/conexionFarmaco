package com.example.conexionfarmaco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import android.widget.ImageView;
import org.json.JSONObject;
import java.util.List;
import android.util.Log;

public class Utilidades {
    static String url_consulta = "http://192.168.101.5:5984/usuarios/_design/usuarios/_view/usuarios";
    static String url_mto = "http://192.168.101.5:5984/usuarios";
    static String url_find = "http://192.168.101.5:5984/usuarios/_find";
    
    // Rutas para el sistema de Farmacias
    static String url_farmacias = "http://192.168.101.5:5984/farmacias";
    static String url_medicamentos = "http://192.168.101.5:5984/medicamentos";
    static String url_pedidos = "http://192.168.101.5:5984/pedidos";
    
    // Selectores CouchDB
    static String url_find_farmacias = "http://192.168.101.5:5984/farmacias/_find";
    static String url_find_medicamentos = "http://192.168.101.5:5984/medicamentos/_find";
    static String url_find_pedidos = "http://192.168.101.5:5984/pedidos/_find";

    static String user = "steven";
    static String passwd = "200612";
    static String credencialesCodificadas = Base64.encodeToString((user + ":" + passwd).getBytes(), Base64.NO_WRAP);

    public static String generarId() {
        return java.util.UUID.randomUUID().toString();
    }

    public static boolean hayInternet(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    public static void sincronizar(Context context) {
        if (!hayInternet(context)) return;

        DBHelper dbHelper = new DBHelper(context);
        List<JSONObject> pendientes = dbHelper.obtenerPendientes();

        for (JSONObject p : pendientes) {
            new Thread(() -> {
                try {
                    String id_local = p.getString("id_local");
                    String url = p.getString("url");
                    String metodo = p.getString("metodo");
                    String json = p.getString("json");
                    String tipo = p.getString("tipo");

                    if (tipo.equals("couchdb")) {
                        TareaServidor tarea = new TareaServidor();
                        String res = tarea.execute(json, metodo, url).get();
                        JSONObject resJson = new JSONObject(res);
                        
                        // Si se guardó (ok:true) o si hay conflicto (409) significa que ya existe
                        if (resJson.optBoolean("ok", false) || res.contains("409")) {
                            dbHelper.eliminarPendiente(id_local);
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
                    Log.e("Sync", "Error sincronizando: " + e.getMessage());
                }
            }).start();
        }
    }


    public static void cargarImagenBase64(String base64, ImageView iv) {
        if (base64 != null && !base64.isEmpty()) {
            try {
                // Eliminar filtros de color y limpiar la vista antes de cargar
                iv.setColorFilter(null);
                iv.setPadding(0, 0, 0, 0);
                iv.setBackground(null);

                // Manejar prefijos si existen
                if (base64.contains(",")) {
                    base64 = base64.split(",")[1];
                }

                byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                if (decodedByte != null) {
                    iv.setImageBitmap(decodedByte);
                }
            } catch (Exception e) {
                Log.e("Utilidades", "Error decodificando imagen", e);
            }
        }
    }

}
