package com.example.conexionfarmaco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import android.widget.ImageView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.FutureCallback;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingWorkPolicy;

import com.google.ai.client.generativeai.type.BlockThreshold;
import com.google.ai.client.generativeai.type.HarmCategory;
import com.google.ai.client.generativeai.type.SafetySetting;
import com.google.ai.client.generativeai.type.RequestOptions;
import java.util.Collections;

public class Utilidades {
    // ... URLs ...
    static String url_consulta = "http://192.168.1.158:5984/usuarios/_design/usuarios/_view/usuarios";
    static String url_mto = "http://192.168.1.158:5984/usuarios";
    static String url_find = "http://192.168.1.158:5984/usuarios/_find";
    
    // Rutas para el sistema de Farmacias
    static String url_farmacias = "http://192.168.1.158:5984/farmacias";
    static String url_medicamentos = "http://192.168.1.158:5984/medicamentos";
    static String url_pedidos = "http://192.168.1.158:5984/pedidos";
    
    // Selectores CouchDB
    static String url_find_farmacias = "http://192.168.1.158:5984/farmacias/_find";
    static String url_find_medicamentos = "http://192.168.1.158:5984/medicamentos/_find";
    static String url_find_pedidos = "http://192.168.1.158:5984/pedidos/_find";

    static String user = "steven";
    static String passwd = "200612";
    static String credencialesCodificadas = Base64.encodeToString((user + ":" + passwd).getBytes(), Base64.NO_WRAP);

    private static final String GEMINI_API_KEY = "#";

    private static GenerativeModelFutures modelInstance;

    public static GenerativeModelFutures getGeminiModel() {
        if (modelInstance == null) {
            RequestOptions requestOptions = new RequestOptions(60000L, "v1");
            GenerativeModel gm = new GenerativeModel(
                    "gemini-2.5-flash",
                    GEMINI_API_KEY,
                    null, // generationConfig
                    null, // safetySettings
                    requestOptions
            );
            modelInstance = GenerativeModelFutures.from(gm);
        }
        return modelInstance;
    }

    public static void consultarIA(Context context, String preguntaCliente, FutureCallback<GenerateContentResponse> callback) {
        GenerativeModelFutures model = getGeminiModel();

        StringBuilder inventario = new StringBuilder();
        try {
            DBHelper db = new DBHelper(context);
            List<JSONObject> todos = db.obtenerMedicamentosCache(null, false);
            
            // Filtro ultra-rápido: solo enviamos lo que coincida con la pregunta
            int encontrados = 0;
            String q = preguntaCliente.toLowerCase();
            for (JSONObject med : todos) {
                if (encontrados >= 10) break; // Límite estricto de 10 items para no agotar cuota
                String nombre = med.optString("nombre", "").toLowerCase();
                if (q.contains(nombre) || nombre.contains(q)) {
                    inventario.append("- ").append(med.optString("nombre"))
                            .append(": $").append(med.optString("precio"))
                            .append(" en ").append(med.optString("nombre_farmacia"))
                            .append(" (Stock: ").append(med.optString("stock")).append(")\n");
                    encontrados++;
                }
            }
        } catch (Exception e) {
            Log.e("IA", "Error", e);
        }

        String prompt = "Eres farmacéutico. Inventario actual:\n" + inventario +
                "\nInstrucción: Si el producto está arriba con stock > 0, di dónde está y precio. Si no está, di que no hay. No uses asteriscos ni negritas. Sé breve.\n" +
                "Pregunta: " + preguntaCliente;

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(response, callback, java.util.concurrent.Executors.newSingleThreadExecutor());
    }

    public static String generarId() {
        return java.util.UUID.randomUUID().toString();
    }

    public static boolean hayInternet(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    public static void sincronizar(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest =
                new OneTimeWorkRequest.Builder(SyncWorker.class)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "SincronizacionUnica",
                ExistingWorkPolicy.REPLACE,
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
            iv.setColorFilter(null);
            iv.setPadding(0, 0, 0, 0);

            if (base64.startsWith("content://") || base64.startsWith("file://")) {
                iv.setImageURI(android.net.Uri.parse(base64));
                return;
            }

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

            String pureBase64 = base64;
            if (pureBase64.contains(",")) {
                pureBase64 = pureBase64.split(",")[1];
            }

            // OPTIMIZACIÓN: Evitar replaceAll con regex en strings gigantes. 
            // Base64.decode ya maneja espacios y saltos de línea con los flags adecuados.
            byte[] decodedString = Base64.decode(pureBase64, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            if (decodedByte != null) {
                iv.setImageBitmap(decodedByte);
            }
        } catch (Exception e) {
            Log.e("Utilidades", "Error cargando imagen: " + e.getMessage());
        }
    }

    // --- CONFIGURACIÓN WOMPI (EL SALVADOR) ---
    private static final String WOMPI_APP_ID = "fc68398a-4e60-461f-9b60-1f53403b7c56";
    private static final String WOMPI_API_SECRET = "ac3673d4-2a57-40ae-a015-3a33aa8aae5b"; // Tu Secret de la imagen
    private static final String WOMPI_REDIRECT_URL = "https://pago-finalizado.com/";

    /**
     * Integración Dinámica: Obtiene un enlace de pago real desde la API de Wompi.
     */
    public static void pagarConWompi(Context context, double monto, String referencia) {
        android.widget.Toast.makeText(context, "Conectando con pasarela de pago...", android.widget.Toast.LENGTH_LONG).show();
        
        new Thread(() -> {
            try {
                // 1. Obtener Token de Acceso
                java.net.URL urlToken = new java.net.URL("https://id.wompi.sv/connect/token");
                java.net.HttpURLConnection connToken = (java.net.HttpURLConnection) urlToken.openConnection();
                connToken.setRequestMethod("POST");
                connToken.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connToken.setDoOutput(true);

                String dataToken = "grant_type=client_credentials" +
                        "&client_id=" + WOMPI_APP_ID +
                        "&client_secret=" + WOMPI_API_SECRET +
                        "&audience=wompi_api";

                try (java.io.OutputStream osToken = connToken.getOutputStream()) {
                    osToken.write(dataToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                if (connToken.getResponseCode() != 200) {
                    throw new Exception("Error al obtener token: " + connToken.getResponseCode());
                }

                java.io.BufferedReader brToken = new java.io.BufferedReader(new java.io.InputStreamReader(connToken.getInputStream()));
                StringBuilder resToken = new StringBuilder();
                String line;
                while ((line = brToken.readLine()) != null) resToken.append(line);
                String token = new JSONObject(resToken.toString()).getString("access_token");

                // 2. Crear Enlace de Pago Dinámico
                java.net.URL urlEnlace = new java.net.URL("https://api.wompi.sv/EnlacePago");
                java.net.HttpURLConnection connEnlace = (java.net.HttpURLConnection) urlEnlace.openConnection();
                connEnlace.setRequestMethod("POST");
                connEnlace.setRequestProperty("Authorization", "Bearer " + token);
                connEnlace.setRequestProperty("Content-Type", "application/json");
                connEnlace.setRequestProperty("Accept", "application/json");
                connEnlace.setDoOutput(true);

                JSONObject body = new JSONObject();
                // Referencia única para el comercio
                body.put("identificadorEnlaceComercio", referencia.replaceAll("[^a-zA-Z0-9]", "") + System.currentTimeMillis() % 1000);
                body.put("monto", monto);
                body.put("nombreProducto", "Compra Farmacia");
                body.put("idAplicativo", WOMPI_APP_ID); // Obligatorio en algunos entornos de SV
                
                JSONObject configuracion = new JSONObject();
                configuracion.put("urlRedirect", WOMPI_REDIRECT_URL);
                configuracion.put("esMontoEditable", false);
                // Wompi requiere un email de notificación si se usa el objeto configuración
                configuracion.put("emailsNotificacion", "jeffersonk20castillo@gmail.com");
                body.put("configuracion", configuracion);

                try (java.io.OutputStream osEnlace = connEnlace.getOutputStream()) {
                    osEnlace.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int code = connEnlace.getResponseCode();
                if (code == 200 || code == 201) {
                    java.io.BufferedReader brEnlace = new java.io.BufferedReader(new java.io.InputStreamReader(connEnlace.getInputStream()));
                    StringBuilder resEnlace = new StringBuilder();
                    while ((line = brEnlace.readLine()) != null) resEnlace.append(line);
                    
                    String urlPago = new JSONObject(resEnlace.toString()).getString("urlEnlace");

                    // 3. Abrir en la WebView interna de la App
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        android.content.Intent intent = new android.content.Intent(context, PagoActivity.class);
                        intent.putExtra("urlPago", urlPago);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    });
                } else {
                    // Leer error detallado si es posible
                    java.io.InputStream is = connEnlace.getErrorStream();
                    if (is != null) {
                        java.io.BufferedReader brErr = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                        StringBuilder resErr = new StringBuilder();
                        while ((line = brErr.readLine()) != null) resErr.append(line);
                        Log.e("WompiAPI", "Error detallado: " + resErr.toString());
                    }
                    throw new Exception("HTTP " + code);
                }

            } catch (Exception e) {
                Log.e("WompiAPI", "Error: " + e.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    android.widget.Toast.makeText(context, "Error de conexión: Verifique credenciales", android.widget.Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
