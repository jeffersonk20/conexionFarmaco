package com.example.conexionfarmaco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class FarmaciaDetalleActivity extends AppCompatActivity {

    private TextView tvNombre, tvDesc;
    private LinearLayout containerMed;
    private String farmaciaId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farmacia_detalle);

        tvNombre = findViewById(R.id.tvDetalleNombreFarmacia);
        tvDesc = findViewById(R.id.tvDetalleDescFarmacia);
        containerMed = findViewById(R.id.containerMedicamentosFarmacia);

        findViewById(R.id.toolbarDetalle).setOnClickListener(v -> finish());

        String data = getIntent().getStringExtra("farmaciaData");
        if (data != null) {
            try {
                JSONObject farmacia = new JSONObject(data);
                farmaciaId = farmacia.getString("_id");
                tvNombre.setText(farmacia.getString("empresa"));
                tvDesc.setText(farmacia.optString("descripcion", ""));
                
                cargarMedicamentos();
            } catch (Exception e) {}
        }
    }

    private void cargarMedicamentos() {
        new Thread(() -> {
            try {
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    JSONObject query = new JSONObject();
                    query.put("id_farmacia", farmaciaId);
                    selector.put("selector", query);

                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        for (int i = 0; i < docs.length(); i++) {
                            db.guardarMedicamentoLocal(docs.getJSONObject(i));
                        }
                        runOnUiThread(() -> mostrarMedicamentos(docs));
                        return;
                    }
                }

                // Fallback Offline: Buscar en el cache local de medicamentos
                DBHelper db = new DBHelper(this);
                java.util.List<JSONObject> cache = db.obtenerMedicamentosCache(null, false);
                JSONArray filtrados = new JSONArray();
                for (JSONObject med : cache) {
                    if (med.optString("id_farmacia").equals(farmaciaId)) {
                        filtrados.put(med);
                    }
                }
                runOnUiThread(() -> mostrarMedicamentos(filtrados));

            } catch (Exception e) {
                Log.e("FarmaciaDetalle", "Error", e);
            }
        }).start();
    }

    private void mostrarMedicamentos(JSONArray docs) {
        containerMed.removeAllViews();
        if (docs.length() == 0) {
            TextView tv = new TextView(this);
            tv.setText("Esta farmacia aún no tiene medicamentos registrados.");
            tv.setPadding(20, 50, 20, 20);
            tv.setGravity(android.view.Gravity.CENTER);
            containerMed.addView(tv);
        } else {
            for (int i = 0; i < docs.length(); i++) {
                try {
                    agregarCardMedicamento(docs.getJSONObject(i));
                } catch (Exception e) {}
            }
        }
    }


    private void agregarCardMedicamento(JSONObject med) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_medicamento_cliente, null);
        TextView tvNom = card.findViewById(R.id.tvMedNombre);
        TextView tvFarm = card.findViewById(R.id.tvMedFarmacia);
        TextView tvPre = card.findViewById(R.id.tvMedPrecio);
        TextView tvPres = card.findViewById(R.id.tvMedPresentacion);

        tvNom.setText(med.getString("nombre"));
        tvFarm.setVisibility(View.GONE); // No es necesario decir el nombre de la farmacia porque ya estamos en su detalle
        tvPre.setText("$" + med.getString("precio"));
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

        containerMed.addView(card);
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
