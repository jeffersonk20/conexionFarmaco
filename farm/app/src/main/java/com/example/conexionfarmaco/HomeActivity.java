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

    private void cargarDatosUsuario() {
        try {
            String userData = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userData", "");
            if (!userData.isEmpty()) {
                JSONObject user = new JSONObject(userData);
                userEnfermedades = user.optString("enfermedades", "Ninguna");
                userAlergias = user.optString("alergias", "");
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
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        for (int i = 0; i < docs.length(); i++) {
                            JSONObject med = docs.getJSONObject(i);
                            db.guardarMedicamentoLocal(med);
                        }
                        
                        runOnUiThread(() -> mostrarListaMedicamentos(docs, containerPromociones));
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
                // Limpiar query de puntos, comas y espacios extra (especialmente para voz)
                String cleanQuery = query.toLowerCase().replaceAll("[.,]", "").trim();
                
                if (Utilidades.hayInternet(this)) {
                    // Normalización extrema para CouchDB: 
                    // Cada vocal en la búsqueda se convierte en un grupo [vocal_con_sin_tilde]
                    String regexQuery = "(?i).*" + cleanQuery
                            .replaceAll("[aáàä]", "[aáàä]")
                            .replaceAll("[eéèë]", "[eéèë]")
                            .replaceAll("[iíìï]", "[iíìï]")
                            .replaceAll("[oóòö]", "[oóòö]")
                            .replaceAll("[uúùü]", "[uúùü]") + ".*";
                    
                    JSONObject selector = new JSONObject();
                    // Buscamos en múltiples campos para mayor efectividad
                    JSONArray orArray = new JSONArray();
                    orOrArray(orArray, "nombre", regexQuery);
                    orOrArray(orArray, "presentacion", regexQuery);
                    orOrArray(orArray, "enfermedad_objetivo", regexQuery);
                    
                    selector.put("selector", new JSONObject().put("$or", orArray));
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        for (int i = 0; i < docs.length(); i++) db.guardarMedicamentoCache(docs.getJSONObject(i));
                        runOnUiThread(() -> mostrarResultadosBusqueda(docs));
                        return;
                    }
                }
                // Cache si offline (el DBHelper ya maneja la flexibilidad con cleanQuery)
                List<JSONObject> cache = new DBHelper(this).obtenerMedicamentosCache(cleanQuery, false);
                runOnUiThread(() -> mostrarResultadosBusqueda(new JSONArray(cache)));
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
