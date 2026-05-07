package com.example.conexionfarmaco;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminHomeActivity extends AppCompatActivity {

    private TextView tvNombreFarmacia, tvSinMedicamentos;
    private LinearLayout containerMedicamentos;
    private String farmaciaId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_home);

        tvNombreFarmacia = findViewById(R.id.tvAdminNombreFarmacia);
        tvSinMedicamentos = findViewById(R.id.tvAdminSinMedicamentos);
        containerMedicamentos = findViewById(R.id.containerMedicamentosAdmin); // Debo añadir este ID al XML o usar el rv
        
        SharedPreferences prefs = getSharedPreferences("AdminPrefs", MODE_PRIVATE);
        farmaciaId = prefs.getString("farmaciaId", "");
        tvNombreFarmacia.setText(prefs.getString("farmaciaNombre", "Mi Farmacia"));

        FloatingActionButton fab = findViewById(R.id.fabAgregarMedicamento);
        fab.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminMedicamentoActivity.class));
        });

        findViewById(R.id.ivAdminProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, AdminPerfilActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMedicamentos();
    }

    private void cargarMedicamentos() {
        if (farmaciaId.isEmpty()) return;

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
                        containerMedicamentos.removeAllViews();
                        if (docs.length() == 0) {
                            tvSinMedicamentos.setVisibility(View.VISIBLE);
                        } else {
                            tvSinMedicamentos.setVisibility(View.GONE);
                            for (int i = 0; i < docs.length(); i++) {
                                try {
                                    JSONObject med = docs.getJSONObject(i);
                                    agregarCardMedicamento(med);
                                } catch (Exception e) {}
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("AdminHome", "Error carga", e);
            }
        }).start();
    }

    private void agregarCardMedicamento(JSONObject med) throws Exception {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        TextView text1 = view.findViewById(android.R.id.text1);
        TextView text2 = view.findViewById(android.R.id.text2);

        String nombre = med.getString("nombre");
        String precio = med.getString("precio");
        String stock = med.optString("stock", "0");
        
        text1.setText(nombre + " - $" + precio);
        text2.setText("Existencias: " + stock + " | " + med.optString("presentacion", ""));
        
        view.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminMedicamentoActivity.class);
            intent.putExtra("medData", med.toString());
            startActivity(intent);
        });

        containerMedicamentos.addView(view);
    }
}
