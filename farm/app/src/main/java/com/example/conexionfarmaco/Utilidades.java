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
