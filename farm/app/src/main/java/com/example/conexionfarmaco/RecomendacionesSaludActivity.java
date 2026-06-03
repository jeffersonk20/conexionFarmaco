package com.example.conexionfarmaco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

public class RecomendacionesSaludActivity extends AppCompatActivity {
    private LinearLayout container;
    private TextView tvTitulo, tvDesc;
    private ProgressBar progress;
    private String enfermedad = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recomendaciones_salud);

        Toolbar toolbar = findViewById(R.id.toolbarRecomendaciones);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        container = findViewById(R.id.containerListaRecomendados);
        tvTitulo = findViewById(R.id.tvTituloEnfermedadDetalle);
        tvDesc = findViewById(R.id.tvDescripcionEnfermedadDetalle);
        progress = findViewById(R.id.progressRecomendaciones);

        cargarDatosUsuario();
        cargarRecomendaciones();
    }

    private void cargarDatosUsuario() {
        try {
            String userData = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userData", "");
            if (!userData.isEmpty()) {
                JSONObject user = new JSONObject(userData);
                enfermedad = user.optString("enfermedades", "Ninguna");
                tvTitulo.setText("Cuidado para " + enfermedad);
                configurarDescripcion();
            }
        } catch (Exception e) {
            Log.e("RecSalud", "Error loading user data", e);
        }
    }

    private void configurarDescripcion() {
        String d = "Medicamentos y suministros recomendados para tu bienestar.";
        switch (enfermedad) {
            case "Diabetes":
                d = "Mantén un control estricto de tu glucosa. Aquí tienes medicamentos y suministros esenciales.";
                break;
            case "Hipertensión":
                d = "Controlar tu presión arterial es vital. Revisa estas opciones para tu tratamiento.";
                break;
            case "Asma":
                d = "Asegúrate de tener siempre tu inhalador a mano. Mira las opciones disponibles.";
                break;
            case "Gastritis":
                d = "Evita irritantes y consulta estos protectores gástricos recomendados.";
                break;
            case "Arritmia":
                d = "Sigue tu tratamiento cardiovascular puntualmente. Consulta fármacos aquí.";
                break;
            case "Obesidad":
                d = "Complementa tu actividad física con estas opciones de control de peso.";
                break;
            case "Hipotiroidismo":
                d = "La constancia es clave en tu tratamiento tiroideo. Revisa existencias.";
                break;
        }
        tvDesc.setText(d);
    }

    private void cargarRecomendaciones() {
        // 1. Mostrar carga y limpiar contenedor
        progress.setVisibility(View.VISIBLE);
        container.removeAllViews();

        // 2. Cargar de cache INMEDIATAMENTE para respuesta instantánea
        DBHelper db = new DBHelper(this);
        List<JSONObject> cache = db.obtenerMedicamentosCache(enfermedad, false);
        if (!cache.isEmpty()) {
            mostrarMedicamentos(cache);
            // Si hay cache, ocultamos el progress pronto para dar sensación de rapidez
            progress.setVisibility(View.GONE);
        }

        // 3. Actualizar desde el servidor en segundo plano
        new Thread(() -> {
            try {
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("enfermedad_objetivo", enfermedad));

                    TareaServidor tarea = new TareaServidor();
                    // Ejecutar sin .get() para no bloquear este hilo (aunque ya es un Thread)
                    // pero usaremos una lógica más limpia
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    JSONObject resJson = new JSONObject(res);
                    
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        List<JSONObject> freshList = new ArrayList<>();
                        for (int i = 0; i < docs.length(); i++) {
                            JSONObject med = docs.getJSONObject(i);
                            db.guardarMedicamentoLocal(med);
                            freshList.add(med);
                        }

                        // Si la lista fresca es diferente a la de cache, actualizar UI
                        if (freshList.size() != cache.size()) {
                            runOnUiThread(() -> {
                                container.removeAllViews();
                                mostrarMedicamentos(freshList);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("RecSalud", "Error en actualización de fondo", e);
            } finally {
                runOnUiThread(() -> progress.setVisibility(View.GONE));
            }
        }).start();
    }

    private void mostrarMedicamentos(List<JSONObject> lista) {
        if (lista == null || lista.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No hay medicamentos específicos recomendados para " + enfermedad + " por el momento.");
            empty.setPadding(20, 50, 20, 20);
            empty.setGravity(android.view.Gravity.CENTER);
            container.addView(empty);
            return;
        }

        for (JSONObject med : lista) {
            try {
                container.addView(crearCardMedicamento(med));
            } catch (Exception e) {
                Log.e("RecSalud", "Error al crear card", e);
            }
        }
    }

    private View crearCardMedicamento(JSONObject med) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_medicamento_cliente, null);
        TextView tvNombre = card.findViewById(R.id.tvMedNombre);
        TextView tvFarmacia = card.findViewById(R.id.tvMedFarmacia);
        TextView tvPrecio = card.findViewById(R.id.tvMedPrecio);
        TextView tvPres = card.findViewById(R.id.tvMedPresentacion);
        TextView tvStock = card.findViewById(R.id.tvMedStock);
        Button btnAdd = card.findViewById(R.id.btnAgregarAlCarrito);

        tvNombre.setText(med.getString("nombre"));
        tvFarmacia.setText("Disponible en: " + med.optString("nombre_farmacia", "Farmacia"));
        tvPrecio.setText("$" + med.getString("precio"));
        tvPres.setText(med.optString("presentacion", ""));

        // Manejo de Stock
        int stock = med.optInt("stock", 0);
        if (stock > 0) {
            tvStock.setText("Disponibles: " + stock);
            tvStock.setTextColor(android.graphics.Color.GRAY);
            btnAdd.setEnabled(true);
            btnAdd.setAlpha(1.0f);
        } else {
            tvStock.setText("Agotado");
            tvStock.setTextColor(android.graphics.Color.RED);
            btnAdd.setEnabled(false);
            btnAdd.setAlpha(0.5f);
        }

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

        card.findViewById(R.id.btnAgregarAlCarrito).setOnClickListener(v -> agregarAlCarrito(med));

        return card;
    }

    private void agregarAlCarrito(JSONObject med) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("CartPrefs", MODE_PRIVATE);
            String cartStr = prefs.getString("cart", "[]");
            JSONArray cart = new JSONArray(cartStr);

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