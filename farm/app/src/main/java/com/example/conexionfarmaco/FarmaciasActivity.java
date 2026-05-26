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
                    // Traer todas las farmacias. Si CouchDB tiene muchos documentos,
                    // es mejor buscar por tipo o por un campo que todas tengan.
                    JSONObject selector = new JSONObject();
                    JSONObject query = new JSONObject();
                    // Intentamos traer documentos que tengan el campo 'empresa' (que son las farmacias)
                    query.put("empresa", new JSONObject().put("$exists", true));
                    selector.put("selector", query);
                    
                    // Optimización: Solo traer campos necesarios
                    JSONArray fields = new JSONArray();
                    fields.put("_id"); fields.put("_rev"); fields.put("empresa");
                    fields.put("direccion"); fields.put("telefono"); fields.put("correo");
                    fields.put("foto"); fields.put("descripcion"); fields.put("chat_habilitado");
                    selector.put("fields", fields);
                    selector.put("limit", 100);
                    
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                    Log.d("FarmaciasAct", "Respuesta servidor: " + res);
                    
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        db.limpiarFarmaciasCache();
                        for (int i = 0; i < docs.length(); i++) {
                            db.guardarFarmaciaCache(docs.getJSONObject(i));
                        }
                        // Limpiar medicamentos que pertenecían a farmacias borradas
                        db.limpiarDatosHuerfanos();

                        runOnUiThread(() -> mostrarFarmacias(docs));
                        return;
                    }
                }
                // Si offline o falla red
                List<JSONObject> cache = new DBHelper(this).obtenerFarmaciasCache();
                Log.d("FarmaciasAct", "Cargando del cache: " + cache.size() + " farmacias");
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
        if (ivLogo != null) {
            Utilidades.cargarImagenBase64(fotoPath, ivLogo);
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
