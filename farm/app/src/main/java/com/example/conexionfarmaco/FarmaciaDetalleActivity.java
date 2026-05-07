package com.example.conexionfarmaco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
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
                JSONObject selector = new JSONObject();
                JSONObject query = new JSONObject();
                query.put("id_farmacia", farmaciaId);
                selector.put("selector", query);

                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                
                JSONObject resJson = new JSONObject(res);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    runOnUiThread(() -> {
                        containerMed.removeAllViews();
                        for (int i = 0; i < docs.length(); i++) {
                            try {
                                agregarCardMedicamento(docs.getJSONObject(i));
                            } catch (Exception e) {}
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("FarmaciaDetalle", "Error", e);
            }
        }).start();
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

        containerMed.addView(card);
    }
}
