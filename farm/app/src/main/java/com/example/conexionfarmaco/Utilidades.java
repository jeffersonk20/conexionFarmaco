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
    static String url_consulta = "http://192.168.101.10:5984/usuarios/_design/usuarios/_view/usuarios";
    static String url_mto = "http://192.168.101.10:5984/usuarios";
    static String url_find = "http://192.168.101.10:5984/usuarios/_find";
    
    // Rutas para el sistema de Farmacias
    static String url_farmacias = "http://192.168.101.10:5984/farmacias";
    static String url_medicamentos = "http://192.168.101.10:5984/medicamentos";
    static String url_pedidos = "http://192.168.101.10:5984/pedidos";
    
    // Selectores CouchDB
    static String url_find_farmacias = "http://192.168.101.10:5984/farmacias/_find";
    static String url_find_medicamentos = "http://192.168.101.10:5984/medicamentos/_find";
    static String url_find_pedidos = "http://192.168.101.10:5984/pedidos/_find";

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
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        androidx.work.OneTimeWorkRequest syncRequest =
                new androidx.work.OneTimeWorkRequest.Builder(SyncWorker.class)
                        .setConstraints(constraints)
                        .build();

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "SincronizacionUnica",
                androidx.work.ExistingWorkPolicy.REPLACE,
                syncRequest
        );
        Log.d("Sync", "Tarea de sincronización encolada (Única)");
    }

    public static String getEstadoPedidoColor(String estado) {
        switch (estado.toLowerCase()) {
            case "pendiente": return "#FFC107"; // Amber
            case "en camino": return "#2196F3"; // Blue
            case "entregado": return "#4CAF50"; // Green
            case "cancelado": return "#F44336"; // Red
            default: return "#9E9E9E"; // Grey
        }
    }


    public static String normalizar(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
    }

    public static void cargarImagenBase64(String base64, ImageView iv) {
        if (base64 == null || base64.isEmpty()) return;

        try {
            // Limpiar filtros y padding previos
            iv.setColorFilter(null);
            iv.setPadding(0, 0, 0, 0);

            // 1. Manejar URIs directas (galería, cámara, etc.)
            if (base64.startsWith("content://") || base64.startsWith("file://")) {
                iv.setImageURI(android.net.Uri.parse(base64));
                return;
            }

            // 2. Manejar rutas de archivos locales (que empiecen por /)
            // Solo si la cadena es corta (una ruta no suele ser tan larga como un Base64)
            if (base64.startsWith("/") && base64.length() < 1000) {
                java.io.File file = new java.io.File(base64);
                if (file.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (bmp != null) {
                        iv.setImageBitmap(bmp);
                        return;
                    }
                }
            }

            // 3. Procesar como Base64 (para fotos guardadas en la nube)
            String pureBase64 = base64;
            if (pureBase64.contains(",")) {
                pureBase64 = pureBase64.split(",")[1];
            }

            // Limpieza total de espacios y saltos de línea para evitar corrupción
            pureBase64 = pureBase64.trim().replaceAll("\\s+", "");

            byte[] decodedString = Base64.decode(pureBase64, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            if (decodedByte != null) {
                iv.setImageBitmap(decodedByte);
            }
        } catch (Exception e) {
            Log.e("Utilidades", "Error cargando imagen: " + e.getMessage());
        }
    }

}
