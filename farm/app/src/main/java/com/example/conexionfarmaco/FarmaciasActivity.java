package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class FarmaciasActivity extends AppCompatActivity {

    private LinearLayout containerFarmacias;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farmacias);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        containerFarmacias = findViewById(R.id.listFarmacias);
        
        cargarFarmacias();
    }

    private void cargarFarmacias() {
        new Thread(() -> {
            try {
                // Consultar todas las farmacias registradas
                JSONObject selector = new JSONObject();
                selector.put("selector", new JSONObject().put("tipo", "farmacia"));
                
                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                
                JSONObject resJson = new JSONObject(res);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    runOnUiThread(() -> {
                        containerFarmacias.removeAllViews();
                        for (int i = 0; i < docs.length(); i++) {
                            try {
                                JSONObject farmacia = docs.getJSONObject(i);
                                agregarCardFarmacia(farmacia);
                            } catch (Exception e) {}
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("FarmaciasAct", "Error carga", e);
            }
        }).start();
    }

    private void agregarCardFarmacia(JSONObject farmacia) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_farmacia_cliente, null);
        TextView tvNombre = card.findViewById(R.id.tvItemFarmaciaNombre);
        TextView tvDesc = card.findViewById(R.id.tvItemFarmaciaDesc);
        ImageView ivLogo = card.findViewById(R.id.ivItemFarmaciaLogo);
        
        tvNombre.setText(farmacia.getString("empresa"));
        tvDesc.setText(farmacia.optString("descripcion", "Sin descripción disponible"));

        String fotoPath = farmacia.optString("foto", "");
        if (!fotoPath.isEmpty() && ivLogo != null) {
            try {
                ivLogo.setImageURI(android.net.Uri.parse(fotoPath));
            } catch (Exception e) {
                ivLogo.setImageResource(R.mipmap.ic_launcher);
            }
        }

        card.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(this, FarmaciaDetalleActivity.class);
                intent.putExtra("farmaciaData", farmacia.toString());
                startActivity(intent);
            } catch (Exception e) {}
        });

        containerFarmacias.addView(card);
    }
}
