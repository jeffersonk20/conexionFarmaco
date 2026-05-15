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

    private LinearLayout containerPromociones, containerRecomendados, containerBusqueda;
    private EditText etBuscador;
    private TextView tvDestacados, tvRecomendados;
    private String userEnfermedades = "", userAlergias = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        containerPromociones = findViewById(R.id.containerPromociones);
        containerRecomendados = findViewById(R.id.containerRecomendados);
        containerBusqueda = findViewById(R.id.containerBusqueda);
        etBuscador = findViewById(R.id.etBuscadorHome);
        tvDestacados = findViewById(R.id.tvDestacados);
        tvRecomendados = findViewById(R.id.tvRecomendados);

        // Obtener datos del usuario para recomendaciones
        cargarDatosUsuario();

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
        cargarRecomendaciones();

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

    private void mostrarModoBusqueda(boolean busquedaActiva) {
        if (busquedaActiva) {
            tvDestacados.setVisibility(View.GONE);
            containerPromociones.setVisibility(View.GONE);
            tvRecomendados.setVisibility(View.GONE);
            containerRecomendados.setVisibility(View.GONE);
            containerBusqueda.setVisibility(View.VISIBLE);
        } else {
            tvDestacados.setVisibility(View.VISIBLE);
            containerPromociones.setVisibility(View.VISIBLE);
            
            // Solo mostrar recomendaciones si hay elementos y no es el placeholder de "vacio"
            if (containerRecomendados.getChildCount() > 0) {
                View firstChild = containerRecomendados.getChildAt(0);
                if (firstChild.getTag() == null || !firstChild.getTag().equals("empty")) {
                    tvRecomendados.setVisibility(View.VISIBLE);
                    containerRecomendados.setVisibility(View.VISIBLE);
                }
            }
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
                        for (int i = 0; i < docs.length(); i++) db.guardarMedicamentoCache(docs.getJSONObject(i));
                        
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


    private void cargarRecomendaciones() {
        if (userEnfermedades.equalsIgnoreCase("Ninguna") && userAlergias.isEmpty()) {
            runOnUiThread(() -> {
                tvRecomendados.setVisibility(View.GONE);
                containerRecomendados.setVisibility(View.GONE);
                View v = new View(this); v.setTag("empty"); containerRecomendados.addView(v);
            });
            return;
        }

        new Thread(() -> {
            try {
                // Construir términos de búsqueda basados en salud
                StringBuilder regexBuilder = new StringBuilder("(?i)");
                boolean hasTerms = false;

                if (!userEnfermedades.equalsIgnoreCase("Ninguna")) {
                    regexBuilder.append(userEnfermedades).append("|");
                    // Añadir palabras clave relacionadas
                    if (userEnfermedades.contains("Diabetes")) regexBuilder.append("insulina|metformina|glibenclamida|glucosa|");
                    if (userEnfermedades.contains("Hipertensión")) regexBuilder.append("enalapril|losartan|amlodipino|presion|");
                    if (userEnfermedades.contains("Asma")) regexBuilder.append("salbutamol|inhalador|montelukast|");
                    if (userEnfermedades.contains("Gastritis")) regexBuilder.append("omeprazol|pantoprazol|antiacido|");
                    hasTerms = true;
                }
                
                if (!userAlergias.isEmpty()) {
                    regexBuilder.append("loratadina|cetirizina|clorfenamina|alergia|");
                    hasTerms = true;
                }

                if (!hasTerms) return;
                
                String finalRegex = regexBuilder.toString();
                if (finalRegex.endsWith("|")) finalRegex = finalRegex.substring(0, finalRegex.length() - 1);

                JSONObject selector = new JSONObject();
                JSONArray orArray = new JSONArray();
                
                orArray.put(new JSONObject().put("nombre", new JSONObject().put("$regex", finalRegex)));
                orArray.put(new JSONObject().put("presentacion", new JSONObject().put("$regex", finalRegex)));
                
                selector.put("selector", new JSONObject().put("$or", orArray));
                selector.put("limit", 5);

                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                
                JSONObject resJson = new JSONObject(res);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    runOnUiThread(() -> {
                        containerRecomendados.removeAllViews();
                        if (docs.length() > 0) {
                            tvRecomendados.setVisibility(View.VISIBLE);
                            containerRecomendados.setVisibility(View.VISIBLE);
                            for (int i = 0; i < docs.length(); i++) {
                                try {
                                    containerRecomendados.addView(crearCardMedicamento(docs.getJSONObject(i)));
                                } catch (Exception e) {}
                            }
                        } else {
                            tvRecomendados.setVisibility(View.GONE);
                            containerRecomendados.setVisibility(View.GONE);
                            View v = new View(this); v.setTag("empty"); containerRecomendados.addView(v);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("HomeAct", "Error recomendados", e);
            }
        }).start();
    }

    private void buscarMedicamentos(String query) {
        new Thread(() -> {
            try {
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("nombre", new JSONObject().put("$regex", "(?i)" + query)));
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
                // Cache si offline
                List<JSONObject> cache = new DBHelper(this).obtenerMedicamentosCache(query, false);
                runOnUiThread(() -> mostrarResultadosBusqueda(new JSONArray(cache)));
            } catch (Exception e) {
                Log.e("HomeAct", "Error busqueda", e);
            }
        }).start();
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
