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
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout containerHome;
    private EditText etBuscador;
    private TextView tvTituloSeccion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        containerHome = findViewById(R.id.containerHomeContenido);
        etBuscador = findViewById(R.id.etBuscadorHome); // Debo añadir este ID al XML
        tvTituloSeccion = findViewById(R.id.tvDestacados);

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

        // Cargar promociones al inicio
        cargarPromociones();

        // Configurar buscador
        if (etBuscador != null) {
            etBuscador.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String query = s.toString().trim();
                    if (query.length() > 2) {
                        buscarMedicamentos(query);
                    } else if (query.isEmpty()) {
                        cargarPromociones();
                    }
                }
            });
        }
    }

    private void cargarPromociones() {
        tvTituloSeccion.setText("Productos destacados / Promociones");
        new Thread(() -> {
            try {
                JSONObject selector = new JSONObject();
                selector.put("selector", new JSONObject().put("promocion", true));
                
                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                
                JSONObject resJson = new JSONObject(res);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    runOnUiThread(() -> {
                        containerHome.removeAllViews();
                        for (int i = 0; i < docs.length(); i++) {
                            try {
                                agregarCardMedicamento(docs.getJSONObject(i));
                            } catch (Exception e) {}
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("HomeAct", "Error promos", e);
            }
        }).start();
    }

    private void buscarMedicamentos(String query) {
        tvTituloSeccion.setText("Resultados de búsqueda");
        new Thread(() -> {
            try {
                JSONObject selector = new JSONObject();
                JSONObject regex = new JSONObject();
                regex.put("$regex", "(?i)" + query); // Búsqueda insensible a mayúsculas
                selector.put("selector", new JSONObject().put("nombre", regex));

                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();

                JSONObject resJson = new JSONObject(res);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    runOnUiThread(() -> {
                        containerHome.removeAllViews();
                        if (docs.length() == 0) {
                            TextView tv = new TextView(this);
                            tv.setText("No se encontraron medicamentos");
                            tv.setPadding(20, 50, 20, 20);
                            tv.setGravity(android.view.Gravity.CENTER);
                            containerHome.addView(tv);
                        } else {
                            for (int i = 0; i < docs.length(); i++) {
                                try {
                                    agregarCardMedicamento(docs.getJSONObject(i));
                                } catch (Exception e) {}
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("HomeAct", "Error busqueda", e);
            }
        }).start();
    }

    private void agregarCardMedicamento(JSONObject med) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_medicamento_cliente, null);
        TextView tvNombre = card.findViewById(R.id.tvMedNombre);
        TextView tvFarmacia = card.findViewById(R.id.tvMedFarmacia);
        TextView tvPrecio = card.findViewById(R.id.tvMedPrecio);
        TextView tvPres = card.findViewById(R.id.tvMedPresentacion);

        tvNombre.setText(med.getString("nombre"));
        tvFarmacia.setText("Disponible en: " + med.optString("nombre_farmacia", "Farmacia"));
        tvPrecio.setText("$" + med.getString("precio"));
        tvPres.setText(med.optString("presentacion", ""));

        containerHome.addView(card);
    }
}
