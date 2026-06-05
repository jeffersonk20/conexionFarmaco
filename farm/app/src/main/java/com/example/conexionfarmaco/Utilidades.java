package com.example.conexionfarmaco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import android.widget.ImageView;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.media.ExifInterface;
import android.graphics.Matrix;
import android.net.Uri;
import java.io.InputStream;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.LruCache;

public class Utilidades {
    private static LruCache<String, Bitmap> bitmapCache;
    private static final ExecutorService imageExecutor = Executors.newFixedThreadPool(4);

    static {
        // Inicializar cache (1/8 de la memoria disponible)
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    // ... URLs ...
    static String url_consulta = "http://10.184.134.188:5984/usuarios/_design/usuarios/_view/usuarios";
    static String url_mto = "http://10.184.134.188:5984/usuarios";
    static String url_find = "http://10.184.134.188:5984/usuarios/_find";
    
    // Rutas para el sistema de Farmacias
    static String url_farmacias = "http://10.184.134.188:5984/farmacias";
    static String url_medicamentos = "http://10.184.134.188:5984/medicamentos";
    static String url_pedidos = "http://10.184.134.188:5984/pedidos";
    
    // Selectores CouchDB
    static String url_find_farmacias = "http://10.184.134.188:5984/farmacias/_find";
    static String url_find_medicamentos = "http://10.184.134.188:5984/medicamentos/_find";
    static String url_find_pedidos = "http://10.184.134.188:5984/pedidos/_find";

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
            // Limpiar la pregunta para extraer la palabra clave del medicamento
            // Usamos una limpieza más robusta para capturar el nombre del producto, incluyendo errores como "nececito"
            String queryBusqueda = preguntaCliente.toLowerCase()
                    .replaceAll("(?i)\\b(hola|buenos|dias|tardes|noches|busco|necesito|nececito|tienes|tienen|hay|venden|vende|quisiera|queria|comprar|donde|puedo|encontrar|si|tendra|tendran|existencia|disponible|disponibilidad|por|favor|gracias|quiero|necesitaria)\\b", "")
                    .replaceAll("[¿?¡!]", "")
                    .trim();

            List<JSONObject> recomendados = db.obtenerMedicamentosCache(queryBusqueda, false);

            // Si no hay resultados con la frase completa, intentamos buscar por palabras individuales significativas
            if (recomendados.isEmpty() && queryBusqueda.contains(" ")) {
                for (String palabra : queryBusqueda.split("\\s+")) {
                    if (palabra.length() > 3) {
                        List<JSONObject> partial = db.obtenerMedicamentosCache(palabra, false);
                        for (JSONObject p : partial) {
                            boolean existe = false;
                            for (JSONObject r : recomendados) {
                                if (r.optString("_id").equals(p.optString("_id"))) {
                                    existe = true;
                                    break;
                                }
                            }
                            if (!existe) recomendados.add(p);
                        }
                    }
                }
            }
            
            int encontrados = 0;
            for (JSONObject med : recomendados) {
                if (encontrados >= 5) break; // Suficientes opciones para ser breve
                
                // Manejo robusto de stock (puede venir como String desde SQLite)
                int stock = 0;
                try {
                    Object s = med.opt("stock");
                    if (s instanceof Number) stock = ((Number) s).intValue();
                    else stock = Integer.parseInt(String.valueOf(s));
                } catch (Exception e) {
                    stock = med.optInt("stock", 0);
                }

                if (stock > 0) {
                    inventario.append("PRODUCTO: ").append(med.optString("nombre"))
                            .append(" | PRECIO: $").append(med.optString("precio"))
                            .append(" | FARMACIA: ").append(med.optString("nombre_farmacia"))
                            .append("\n");
                    encontrados++;
                }
            }
        } catch (Exception e) {
            Log.e("IA", "Error", e);
        }

        String prompt = "Actúa como un buscador de inventario farmacéutico estricto. " +
                "DATOS REALES:\n" + (inventario.length() > 0 ? inventario.toString() : "SIN EXISTENCIAS") + "\n\n" +
                "REGLAS:\n" +
                "1. Si el producto solicitado está en los DATOS REALES, responde únicamente: 'Disponible en [Nombre Farmacia] a un precio de $[Precio]'.\n" +
                "2. Si los DATOS REALES son 'SIN EXISTENCIAS', responde exactamente: 'Lo sentimos, no hay disponibilidad de este producto'.\n" +
                "3. Prohibido usar asteriscos, negritas, Markdown o dar consejos médicos.\n" +
                "4. Sé extremadamente breve.\n\n" +
                "Pregunta: " + preguntaCliente;

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(response, callback, java.util.concurrent.Executors.newSingleThreadExecutor());
    }

    public static String generarId() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Descuenta el stock de los medicamentos comprados tanto localmente como en el servidor.
     * Funciona para pagos en efectivo y con tarjeta.
     */
    public static void descontarStock(Context context, JSONArray cartItems) {
        if (cartItems == null || cartItems.length() == 0) {
            Log.w("Stock", "Carrito vacío, nada que descontar.");
            return;
        }
        
        DBHelper db = new DBHelper(context);
        for (int i = 0; i < cartItems.length(); i++) {
            try {
                JSONObject itemCarrito = cartItems.getJSONObject(i);
                
                // Obtener ID único del medicamento (soportar _id o id)
                String idMed = itemCarrito.optString("_id", itemCarrito.optString("id", ""));
                int cantidadComprada = itemCarrito.optInt("cantidad", 0);

                if (idMed.isEmpty() || cantidadComprada <= 0) {
                    Log.w("Stock", "Item omitido: ID '" + idMed + "' inválido o cantidad <= 0 (" + cantidadComprada + ")");
                    continue;
                }

                // 1. Obtener el documento actual desde el cache local
                JSONObject medDoc = db.obtenerMedicamentoPorId(idMed);
                
                if (medDoc == null) {
                    Log.d("Stock", "Medicamento " + idMed + " no en cache, reconstruyendo...");
                    medDoc = new JSONObject(itemCarrito.toString());
                    medDoc.remove("cantidad");
                } else {
                    // Sincronizar campos del carrito por si el cache está incompleto
                    JSONArray keys = itemCarrito.names();
                    if (keys != null) {
                        for (int j = 0; j < keys.length(); j++) {
                            String key = keys.getString(j);
                            if (!key.equals("cantidad") && !medDoc.has(key)) {
                                medDoc.put(key, itemCarrito.get(key));
                            }
                        }
                    }
                }

                // 2. Calcular el nuevo stock
                int stockAnterior = 0;
                Object stockObj = medDoc.opt("stock");
                if (stockObj instanceof Number) {
                    stockAnterior = ((Number) stockObj).intValue();
                } else {
                    try {
                        stockAnterior = Integer.parseInt(String.valueOf(stockObj));
                    } catch (Exception e) {
                        stockAnterior = itemCarrito.optInt("stock", 0);
                    }
                }

                int nuevoStock = Math.max(0, stockAnterior - cantidadComprada);
                medDoc.put("stock", String.valueOf(nuevoStock));

                Log.d("Stock", "Actualizando localmente " + idMed + ": " + stockAnterior + " -> " + nuevoStock);

                // 3. ACTUALIZACIÓN LOCAL INMEDIATA
                db.guardarMedicamentoCache(medDoc, true);

                // 4. ACTUALIZACIÓN EN SERVIDOR (CouchDB)
                String urlDoc = url_medicamentos + "/" + idMed;
                db.agregarPendiente(urlDoc, "PUT", medDoc.toString(), "couchdb");

            } catch (Exception e) {
                Log.e("Utilidades", "Error crítico descontando stock para item en índice " + i, e);
            }
        }
        
        // Disparar sincronización para que el cambio suba a la nube inmediatamente
        sincronizar(context);
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

        // Intentar recuperar de caché primero para evitar decodificación costosa
        Bitmap cached = bitmapCache.get(base64);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }

        // Si no está en caché, decodificar en segundo plano para no congelar la UI
        imageExecutor.execute(() -> {
            try {
                Bitmap bmp = null;
                
                if (base64.startsWith("content://") || base64.startsWith("file://")) {
                    Uri uri = Uri.parse(base64);
                    bmp = obtenerBitmapRotado(iv.getContext(), uri);
                } else if (base64.startsWith("/") && base64.length() < 1000) {
                    java.io.File file = new java.io.File(base64);
                    if (file.exists()) {
                        bmp = obtenerBitmapRotado(file.getAbsolutePath());
                    }
                } else {
                    String pureBase64 = base64;
                    if (pureBase64.contains(",")) {
                        pureBase64 = pureBase64.split(",")[1];
                    }
                    byte[] decodedString = Base64.decode(pureBase64, Base64.DEFAULT);
                    bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                }

                if (bmp != null) {
                    final Bitmap finalBmp = bmp;
                    bitmapCache.put(base64, finalBmp);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        iv.setColorFilter(null);
                        iv.setPadding(0, 0, 0, 0);
                        iv.setImageBitmap(finalBmp);
                    });
                }
            } catch (Exception e) {
                Log.e("Utilidades", "Error cargando imagen: " + e.getMessage());
            }
        });
    }

    public static Bitmap obtenerBitmapRotado(Context context, Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            ExifInterface exifInterface;
            InputStream isExif = context.getContentResolver().openInputStream(uri);
            if (isExif != null) {
                exifInterface = new ExifInterface(isExif);
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                isExif.close();
                return rotarBitmap(bitmap, orientation);
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap obtenerBitmapRotado(String path) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            return rotarBitmap(bitmap, orientation);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap rotarBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
            default: return bitmap;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return rotated;
        } catch (OutOfMemoryError e) {
            return bitmap;
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
