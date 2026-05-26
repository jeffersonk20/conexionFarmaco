package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout containerPromociones, containerBusqueda;
    private EditText etBuscador;
    private TextView tvDestacados, tvTituloInfoSalud;
    private View cardInfoSalud;
    private String userEnfermedades = "", userAlergias = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        containerPromociones = findViewById(R.id.containerPromociones);
        containerBusqueda = findViewById(R.id.containerBusqueda);
        etBuscador = findViewById(R.id.etBuscadorHome);
        tvDestacados = findViewById(R.id.tvDestacados);
        cardInfoSalud = findViewById(R.id.cardInfoSalud);
        tvTituloInfoSalud = findViewById(R.id.tvTituloInfoSalud);

        // Obtener datos del usuario para recomendaciones
        cargarDatosUsuario();
        mostrarBannerSalud();

        if (cardInfoSalud != null) {
            cardInfoSalud.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, RecomendacionesSaludActivity.class);
                startActivity(intent);
            });
        }

        ImageView ivMenu = findViewById(R.id.ivMenu);
        if (ivMenu != null) {
            ivMenu.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, FarmaciasActivity.class);
                startActivity(intent);
            });
        }

        ImageView ivProfile = findViewById(R.id.ivProfile);
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, PerfilActivity.class);
                startActivity(intent);
            });
        }

        ImageView ivCart = findViewById(R.id.ivCart);
        if (ivCart != null) {
            ivCart.setOnClickListener(v -> {
                startActivity(new Intent(this, CarritoActivity.class));
            });
        }

        ImageView ivHistory = findViewById(R.id.ivHistory);
        if (ivHistory != null) {
            ivHistory.setOnClickListener(v -> {
                startActivity(new Intent(this, HistorialPedidosActivity.class));
            });
        }

        // Cargar secciones iniciales
        cargarPromociones();

        findViewById(R.id.ivVoiceSearch).setOnClickListener(v -> {
            Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Diga el nombre del medicamento");
            try {
                startActivityForResult(intent, 100);
            } catch (Exception e) {
                Toast.makeText(this, "Su dispositivo no soporta búsqueda por voz", Toast.LENGTH_SHORT).show();
            }
        });

        // Intentar sincronizar datos pendientes
        Utilidades.sincronizar(this);

        // Abrir Chat IA
        findViewById(R.id.fab_chat_ia).setOnClickListener(v -> {
            startActivity(new Intent(this, ChatIAActivity.class));
        });

        // Configurar buscador
        if (etBuscador != null) {
            etBuscador.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String query = s.toString().trim();
                    if (query.length() > 2) {
                        mostrarModoBusqueda(true);
                        buscarMedicamentos(query);
                    } else if (query.isEmpty()) {
                        mostrarModoBusqueda(false);
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> result = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                // Limpiar el texto de la voz de puntos, comas y espacios extra
                String voiceQuery = result.get(0).replaceAll("[.,]", "").trim();
                etBuscador.setText(voiceQuery);
                mostrarModoBusqueda(true);
                buscarMedicamentos(voiceQuery);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sincronizar farmacias primero para que los medicamentos se puedan mostrar (por la restricción de id_farmacia en DBHelper)
        sincronizarFarmacias();
        // Refrescar promociones por si el admin hizo cambios offline
        cargarPromociones();
        // Intentar sincronizar lo pendiente
        Utilidades.sincronizar(this);
    }

    private void sincronizarFarmacias() {
        new Thread(() -> {
            try {
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("empresa", new JSONObject().put("$exists", true)));
                    // Optimización: Solo traer campos necesarios para que sea rápido
                    JSONArray fields = new JSONArray();
                    fields.put("_id"); fields.put("_rev"); fields.put("empresa");
                    fields.put("direccion"); fields.put("telefono"); fields.put("correo");
                    fields.put("foto"); fields.put("descripcion"); fields.put("chat_habilitado");
                    selector.put("fields", fields);
                    selector.put("limit", 50);

                    String res = new TareaServidor().execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        for (int i = 0; i < docs.length(); i++) {
                            db.guardarFarmaciaCache(docs.getJSONObject(i));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("HomeAct", "Error sincronizando farmacias", e);
            }
        }).start();
    }

    private void cargarDatosUsuario() {
        try {
            String userDataStr = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userData", "");
            if (!userDataStr.isEmpty()) {
                JSONObject user = new JSONObject(userDataStr);
                userEnfermedades = user.optString("enfermedades", "Ninguna");
                userAlergias = user.optString("alergias", "");
                
                // Si enfermedades es un tipo de sangre (error viejo), intentar limpiar
                if (userEnfermedades.contains("+") || userEnfermedades.contains("-")) {
                    userEnfermedades = "Ninguna";
                }
            }
        } catch (Exception e) {
            Log.e("HomeAct", "Error loading user data", e);
        }
    }

    private void mostrarBannerSalud() {
        if (userEnfermedades.equalsIgnoreCase("Ninguna") || userEnfermedades.isEmpty()) {
            cardInfoSalud.setVisibility(View.GONE);
            return;
        }

        cardInfoSalud.setVisibility(View.VISIBLE);
        tvTituloInfoSalud.setText("Cuidado " + userEnfermedades);
    }

    private void mostrarModoBusqueda(boolean busquedaActiva) {
        if (busquedaActiva) {
            tvDestacados.setVisibility(View.GONE);
            containerPromociones.setVisibility(View.GONE);
            containerBusqueda.setVisibility(View.VISIBLE);
        } else {
            tvDestacados.setVisibility(View.VISIBLE);
            containerPromociones.setVisibility(View.VISIBLE);
            containerBusqueda.setVisibility(View.GONE);
        }
    }

    private void cargarPromociones() {
        new Thread(() -> {
            try {
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("promocion", true));
                    // Optimización: Solo traer campos necesarios para el Home
                    JSONArray fields = new JSONArray();
                    fields.put("_id"); fields.put("_rev"); fields.put("id_farmacia");
                    fields.put("nombre"); fields.put("precio"); fields.put("stock");
                    fields.put("presentacion"); fields.put("promocion");
                    fields.put("foto1"); fields.put("foto2"); fields.put("foto3");
                    fields.put("nombre_farmacia"); fields.put("enfermedad_objetivo");
                    selector.put("fields", fields);
                    selector.put("limit", 20); // No traer cientos de productos de golpe

                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        db.limpiarMedicamentosPromocion();
                        for (int i = 0; i < docs.length(); i++) {
                            JSONObject med = docs.getJSONObject(i);
                            db.guardarMedicamentoLocal(med);
                        }
                        
                        // RECARGAR desde el cache local (que ya tiene el ESCUDO de protección)
                        List<JSONObject> promosProtegidas = db.obtenerMedicamentosCache(null, true);
                        runOnUiThread(() -> mostrarListaMedicamentos(new JSONArray(promosProtegidas), containerPromociones));
                        return;
                    }
                }
                // Si no hay internet o falló el server, usar caché
                List<JSONObject> cache = new DBHelper(this).obtenerMedicamentosCache(null, true);
                runOnUiThread(() -> mostrarListaMedicamentos(new JSONArray(cache), containerPromociones));
            } catch (Exception e) {
                Log.e("HomeAct", "Error promos", e);
            }
        }).start();
    }

    private void mostrarListaMedicamentos(JSONArray docs, LinearLayout container) {
        container.removeAllViews();
        for (int i = 0; i < docs.length(); i++) {
            try {
                container.addView(crearCardMedicamento(docs.getJSONObject(i)));
            } catch (Exception e) {}
        }
    }


    private void buscarMedicamentos(String query) {
        new Thread(() -> {
            try {
                DBHelper db = new DBHelper(this);
                String cleanQuery = query.toLowerCase().replaceAll("[.,]", "").trim();
                
                // 1. Carga inmediata desde el cache local (Soporte Offline Total)
                List<JSONObject> cacheResults = db.obtenerMedicamentosCache(cleanQuery, false);
                runOnUiThread(() -> mostrarResultadosBusqueda(new JSONArray(cacheResults)));

                // 2. Si hay internet, intentar buscar en la nube para actualizar
                if (Utilidades.hayInternet(this)) {
                    String regexQuery = "(?i).*" + cleanQuery
                            .replaceAll("[aáàä]", "[aáàä]")
                            .replaceAll("[eéèë]", "[eéèë]")
                            .replaceAll("[iíìï]", "[iíìï]")
                            .replaceAll("[oóòö]", "[oóòö]")
                            .replaceAll("[uúùü]", "[uúùü]") + ".*";
                    
                    JSONObject selector = new JSONObject();
                    JSONArray orArray = new JSONArray();
                    orOrArray(orArray, "nombre", regexQuery);
                    orOrArray(orArray, "presentacion", regexQuery);
                    orOrArray(orArray, "enfermedad_objetivo", regexQuery);
                    
                    selector.put("selector", new JSONObject().put("$or", orArray));
                    // Optimización: Solo traer campos necesarios para la búsqueda
                    JSONArray fields = new JSONArray();
                    fields.put("_id"); fields.put("_rev"); fields.put("id_farmacia");
                    fields.put("nombre"); fields.put("precio"); fields.put("stock");
                    fields.put("presentacion"); fields.put("promocion");
                    fields.put("foto1"); fields.put("foto2"); fields.put("foto3");
                    fields.put("nombre_farmacia"); fields.put("enfermedad_objetivo");
                    selector.put("fields", fields);
                    selector.put("limit", 50);

                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    JSONObject resJson = new JSONObject(res);
                    
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        for (int i = 0; i < docs.length(); i++) db.guardarMedicamentoCache(docs.getJSONObject(i));
                        
                        // RECARGAR desde el cache local (que ya tiene el ESCUDO de protección)
                        List<JSONObject> finalResults = db.obtenerMedicamentosCache(cleanQuery, false);
                        runOnUiThread(() -> mostrarResultadosBusqueda(new JSONArray(finalResults)));
                    }
                }
            } catch (Exception e) {
                Log.e("HomeAct", "Error busqueda", e);
            }
        }).start();
    }

    private void orOrArray(JSONArray arr, String field, String regex) throws Exception {
        arr.put(new JSONObject().put(field, new JSONObject().put("$regex", regex)));
    }

    private void mostrarResultadosBusqueda(JSONArray docs) {
        containerBusqueda.removeAllViews();
        if (docs.length() == 0) {
            TextView tv = new TextView(this);
            tv.setText("No se encontraron resultados");
            tv.setPadding(20, 50, 20, 20);
            tv.setGravity(android.view.Gravity.CENTER);
            containerBusqueda.addView(tv);
        } else {
            for (int i = 0; i < docs.length(); i++) {
                try {
                    containerBusqueda.addView(crearCardMedicamento(docs.getJSONObject(i)));
                } catch (Exception e) {}
            }
        }
    }


    private View crearCardMedicamento(JSONObject med) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_medicamento_cliente, null);
        TextView tvNombre = card.findViewById(R.id.tvMedNombre);
        TextView tvFarmacia = card.findViewById(R.id.tvMedFarmacia);
        TextView tvPrecio = card.findViewById(R.id.tvMedPrecio);
        TextView tvPres = card.findViewById(R.id.tvMedPresentacion);

        tvNombre.setText(med.getString("nombre"));
        tvFarmacia.setText("Disponible en: " + med.optString("nombre_farmacia", "Farmacia"));
        tvPrecio.setText("$" + med.getString("precio"));
        tvPres.setText(med.optString("presentacion", ""));

        // Manejo de fotos
        LinearLayout layoutFotos = card.findViewById(R.id.layoutFotosMed);
        ImageView iv1 = card.findViewById(R.id.ivItemFoto1);
        ImageView iv2 = card.findViewById(R.id.ivItemFoto2);
        ImageView iv3 = card.findViewById(R.id.ivItemFoto3);

        String f1 = med.optString("foto1", "");
        String f2 = med.optString("foto2", "");
        String f3 = med.optString("foto3", "");

        if (!f1.isEmpty() || !f2.isEmpty() || !f3.isEmpty()) {
            layoutFotos.setVisibility(View.VISIBLE);
            if (!f1.isEmpty()) Utilidades.cargarImagenBase64(f1, iv1);
            else iv1.setVisibility(View.GONE);
            
            if (!f2.isEmpty()) Utilidades.cargarImagenBase64(f2, iv2);
            else iv2.setVisibility(View.GONE);
            
            if (!f3.isEmpty()) Utilidades.cargarImagenBase64(f3, iv3);
            else iv3.setVisibility(View.GONE);
        }

        card.findViewById(R.id.btnAgregarAlCarrito).setOnClickListener(v -> {
            agregarAlCarrito(med);
        });

        return card;
    }

    private void agregarAlCarrito(JSONObject med) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("CartPrefs", MODE_PRIVATE);
            String cartStr = prefs.getString("cart", "[]");
            JSONArray cart = new JSONArray(cartStr);

            // Verificar si ya existe
            boolean existe = false;
            for (int i = 0; i < cart.length(); i++) {
                JSONObject item = cart.getJSONObject(i);
                if (item.getString("_id").equals(med.getString("_id"))) {
                    item.put("cantidad", item.getInt("cantidad") + 1);
                    existe = true;
                    break;
                }
            }

            if (!existe) {
                JSONObject newItem = new JSONObject(med.toString());
                newItem.put("cantidad", 1);
                cart.put(newItem);
            }

            prefs.edit().putString("cart", cart.toString()).apply();
            Toast.makeText(this, "Agregado al carrito", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al agregar", Toast.LENGTH_SHORT).show();
        }
    }
}
