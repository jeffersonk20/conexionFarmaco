package com.example.conexionfarmaco;

import android.content.Intent;
import android.net.Uri;
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
import java.util.List;

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
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("tipo", "farmacia"));
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        for (int i = 0; i < docs.length(); i++) db.guardarFarmaciaCache(docs.getJSONObject(i));
                        runOnUiThread(() -> mostrarFarmacias(docs));
                        return;
                    }
                }
                // Si offline o falla red
                List<JSONObject> cache = new DBHelper(this).obtenerFarmaciasCache();
                runOnUiThread(() -> mostrarFarmacias(new JSONArray(cache)));
            } catch (Exception e) {
                Log.e("FarmaciasAct", "Error carga", e);
            }
        }).start();
    }

    private void realizarLlamada(String telefono) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + telefono));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarFarmacias(JSONArray docs) {
        containerFarmacias.removeAllViews();
        for (int i = 0; i < docs.length(); i++) {
            try {
                agregarCardFarmacia(docs.getJSONObject(i));
            } catch (Exception e) {}
        }
    }


    private void agregarCardFarmacia(JSONObject farmacia) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_farmacia_cliente, null);
        TextView tvNombre = card.findViewById(R.id.tvItemFarmaciaNombre);
        TextView tvDesc = card.findViewById(R.id.tvItemFarmaciaDesc);
        ImageView ivLogo = card.findViewById(R.id.ivItemFarmaciaLogo);
        View btnLlamar = card.findViewById(R.id.btnLlamarFarmacia);
        
        tvNombre.setText(farmacia.getString("empresa"));
        tvDesc.setText(farmacia.optString("descripcion", "Sin descripción disponible"));

        String telefono = farmacia.optString("telefono", "");
        if (btnLlamar != null) {
            if (!telefono.isEmpty()) {
                btnLlamar.setOnClickListener(v -> realizarLlamada(telefono));
            } else {
                btnLlamar.setVisibility(View.GONE);
            }
        }

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
