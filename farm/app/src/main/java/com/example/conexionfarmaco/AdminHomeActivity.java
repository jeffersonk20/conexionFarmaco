package com.example.conexionfarmaco;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class AdminHomeActivity extends AppCompatActivity {

    private TextView tvNombreFarmacia, tvSinMedicamentos;
    private LinearLayout containerMedicamentos;
    private EditText etBuscador;
    private String farmaciaId;
    private JSONArray listaOriginalMedicamentos = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_home);

        tvNombreFarmacia = findViewById(R.id.tvAdminNombreFarmacia);
        tvSinMedicamentos = findViewById(R.id.tvAdminSinMedicamentos);
        containerMedicamentos = findViewById(R.id.containerMedicamentosAdmin);
        etBuscador = findViewById(R.id.etAdminBuscadorMedicamento);
        
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

        findViewById(R.id.ivAdminFactura).setOnClickListener(v -> {
            startActivity(new Intent(this, AdminFacturacionActivity.class));
        });

        configurarBuscador();
    }

    private void configurarBuscador() {
        if (etBuscador != null) {
            etBuscador.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    filtrarMedicamentos(s.toString().trim());
                }
            });
        }
    }

    private void filtrarMedicamentos(String query) {
        containerMedicamentos.removeAllViews();
        if (query.isEmpty()) {
            mostrarMedicamentos(listaOriginalMedicamentos);
        } else {
            JSONArray filtrados = new JSONArray();
            for (int i = 0; i < listaOriginalMedicamentos.length(); i++) {
                try {
                    JSONObject med = listaOriginalMedicamentos.getJSONObject(i);
                    if (med.getString("nombre").toLowerCase().contains(query.toLowerCase())) {
                        filtrados.put(med);
                    }
                } catch (Exception e) {}
            }
            mostrarMedicamentos(filtrados);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Utilidades.sincronizar(this);
        cargarMedicamentos();
    }

    private void cargarMedicamentos() {
        if (farmaciaId.isEmpty()) return;

        new Thread(() -> {
            try {
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("id_farmacia", farmaciaId));
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        listaOriginalMedicamentos = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        for (int i = 0; i < listaOriginalMedicamentos.length(); i++) 
                            db.guardarMedicamentoLocal(listaOriginalMedicamentos.getJSONObject(i));
                        
                        actualizarListaAdmin();
                        return;
                    }
                }
                // Cache si offline
                List<JSONObject> cache = new DBHelper(this).obtenerMedicamentosCache(null, false);
                // Filtrar solo los de esta farmacia si el cache es general
                listaOriginalMedicamentos = new JSONArray();
                for (JSONObject m : cache) {
                    if (m.optString("id_farmacia").equals(farmaciaId)) listaOriginalMedicamentos.put(m);
                }
                actualizarListaAdmin();

            } catch (Exception e) {
                Log.e("AdminHome", "Error carga", e);
            }
        }).start();
    }

    private void actualizarListaAdmin() {
        runOnUiThread(() -> {
            String busqueda = etBuscador != null ? etBuscador.getText().toString() : "";
            if (busqueda.isEmpty()) {
                mostrarMedicamentos(listaOriginalMedicamentos);
            } else {
                filtrarMedicamentos(busqueda);
            }
        });
    }


    private void mostrarMedicamentos(JSONArray docs) {
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
    }

    private void agregarCardMedicamento(JSONObject med) throws Exception {
        View view = getLayoutInflater().inflate(R.layout.item_medicamento_admin, containerMedicamentos, false);
        
        TextView tvNombre = view.findViewById(R.id.tvAdminMedItemNombre);
        TextView tvPresentacion = view.findViewById(R.id.tvAdminMedItemPresentacion);
        TextView tvStock = view.findViewById(R.id.tvAdminMedItemStock);
        ImageView ivFoto = view.findViewById(R.id.ivAdminMedItemFoto);

        tvNombre.setText(med.getString("nombre"));
        tvPresentacion.setText(med.optString("presentacion", "Sin presentación"));
        tvStock.setText(med.optString("stock", "0"));
        
        String foto1 = med.optString("foto1", "");
        if (!foto1.isEmpty()) {
            Utilidades.cargarImagenBase64(foto1, ivFoto);
        } else {
            ivFoto.setImageResource(android.R.drawable.ic_menu_camera);
            ivFoto.setColorFilter(ContextCompat.getColor(this, R.color.azul_suave));
        }
        
        view.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminMedicamentoActivity.class);
            intent.putExtra("medData", med.toString());
            startActivity(intent);
        });

        containerMedicamentos.addView(view);
    }
}
