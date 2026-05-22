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

    private static final String GEMINI_API_KEY = "AIzaSyA4ABeoJUsYPHPZ8YL1nNym8q1cx7_pCU4";

    public static GenerativeModelFutures getGeminiModel() {
        // Siguiendo la recomendación de actualización para 2026:
        // Se utiliza gemini-2.5-flash como modelo estable.
        RequestOptions requestOptions = new RequestOptions(60000L, "v1");

        GenerativeModel gm = new GenerativeModel(
                "gemini-2.5-flash",
                GEMINI_API_KEY,
                null, // generationConfig
                null, // safetySettings
                requestOptions
        );
        return GenerativeModelFutures.from(gm);
    }

    public static void consultarIA(Context context, String preguntaCliente, FutureCallback<GenerateContentResponse> callback) {
        GenerativeModelFutures model = getGeminiModel();

        // Obtener inventario de medicamentos filtrado por la pregunta del cliente para ahorrar tokens
        StringBuilder inventario = new StringBuilder();
        try {
            DBHelper db = new DBHelper(context);
            // Intentamos buscar palabras clave de la pregunta en el inventario
            String[] palabras = preguntaCliente.toLowerCase().split("\\s+");
            List<JSONObject> todosLosMedicamenteos = db.obtenerMedicamentosCache(null, false);
            List<JSONObject> relevantes = new ArrayList<>();

            for (JSONObject med : todosLosMedicamenteos) {
                String nombre = med.optString("nombre", "").toLowerCase();
                for (String palabra : palabras) {
                    if (palabra.length() > 3 && nombre.contains(palabra)) {
                        relevantes.add(med);
                        break;
                    }
                }
            }

            // Si no encontramos específicos, incluimos una muestra o informamos
            if (relevantes.isEmpty() && todosLosMedicamenteos.size() < 50) {
                relevantes.addAll(todosLosMedicamenteos); // Si son pocos, mandamos todos
            }

            if (!relevantes.isEmpty()) {
                inventario.append("\nInventario de medicamentos encontrados/relevantes:\n");
                for (JSONObject med : relevantes) {
                    inventario.append("- ").append(med.optString("nombre"))
                            .append(" (").append(med.optString("presentacion")).append(")")
                            .append(" en ").append(med.optString("nombre_farmacia"))
                            .append(". Precio: $").append(med.optString("precio"))
                            .append(". Stock: ").append(med.optString("stock")).append(" unidades.\n");
                }
            } else {
                inventario.append("\nNo se encontraron medicamentos que coincidan específicamente en el inventario local.");
            }
        } catch (Exception e) {
            Log.e("IA", "Error obteniendo inventario", e);
        }

        String prompt = "Eres un asistente experto de la farmacia 'Conexión Fármaco'. " +
                "Tu objetivo es ayudar al cliente con información sobre medicamentos y síntomas." +
                inventario +
                "\n\nInstrucción: Si el cliente pregunta por un medicamento, revisa la lista de arriba. " +
                "Si está disponible y tiene stock > 0, dile en qué farmacia está y el precio. " +
                "Si no está en la lista o el stock es 0, dile que no hay existencias por ahora. " +
                "\n\nPregunta: " + preguntaCliente;

        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(
                response,
                callback,
                java.util.concurrent.Executors.newSingleThreadExecutor()
        );
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
