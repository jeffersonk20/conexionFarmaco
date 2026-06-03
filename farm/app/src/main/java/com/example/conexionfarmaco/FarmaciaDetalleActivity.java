package com.example.conexionfarmaco;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

public class FarmaciaDetalleActivity extends AppCompatActivity {

    private TextView tvNombre, tvDesc, tvBadge;
    private LinearLayout containerMed;
    private android.widget.ProgressBar progress;
    private String farmaciaId, usuarioId;
    private View layoutFabChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farmacia_detalle);

        tvNombre = findViewById(R.id.tvDetalleNombreFarmacia);
        tvDesc = findViewById(R.id.tvDetalleDescFarmacia);
        tvBadge = findViewById(R.id.tvChatBadgeDetalle);
        containerMed = findViewById(R.id.containerMedicamentosFarmacia);
        layoutFabChat = findViewById(R.id.layoutFabChat);
        progress = findViewById(R.id.progressDetalleMed);

        findViewById(R.id.toolbarDetalle).setOnClickListener(v -> finish());

        // Obtener ID de usuario para el contador de mensajes
        try {
            JSONObject userData = new JSONObject(getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userData", "{}"));
            usuarioId = userData.optString("_id", "");
        } catch (Exception e) {}

        String data = getIntent().getStringExtra("farmaciaData");
        if (data != null) {
            try {
                JSONObject farmacia = new JSONObject(data);
                farmaciaId = farmacia.getString("_id");
                tvNombre.setText(farmacia.getString("empresa"));
                tvDesc.setText(farmacia.optString("descripcion", ""));

                ImageView ivLogo = findViewById(R.id.ivDetalleLogoFarmacia);
                String foto = farmacia.optString("foto", "");
                if (!foto.isEmpty()) {
                    ivLogo.setVisibility(View.VISIBLE);
                    Utilidades.cargarImagenBase64(foto, ivLogo);
                }

                String telefono = farmacia.optString("telefono", "");
                FloatingActionButton fab = findViewById(R.id.fabLlamar);
                if (!telefono.isEmpty()) {
                    fab.setOnClickListener(v -> realizarLlamada(telefono));
                } else {
                    fab.setVisibility(View.GONE);
                }

                // Lógica del Chat Directo
                if (farmacia.optBoolean("chat_habilitado", false)) {
                    layoutFabChat.setVisibility(View.VISIBLE);
                    findViewById(R.id.fabChat).setOnClickListener(v -> {
                        Intent intent = new Intent(this, ChatMensajeriaActivity.class);
                        intent.putExtra("id_farmacia", farmaciaId);
                        intent.putExtra("nombre_receptor", tvNombre.getText().toString());
                        intent.putExtra("foto_receptor", farmacia.optString("foto", ""));
                        startActivity(intent);
                    });
                    verificarMensajesNuevos();
                }

                cargarMedicamentos();
            } catch (Exception e) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarContadorBadge();
    }

    private void verificarMensajesNuevos() {
        new Thread(() -> {
            try {
                if (Utilidades.hayInternet(this) && farmaciaId != null && usuarioId != null) {
                    JSONObject selector = new JSONObject();
                    JSONObject query = new JSONObject();
                    query.put("id_farmacia", farmaciaId);
                    query.put("id_usuario", usuarioId);
                    query.put("tipo_doc", "mensaje");
                    selector.put("selector", query);

                    String res = new TareaServidor().execute(selector.toString(), "POST", Utilidades.url_mto + "/_find").get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        for (int i = 0; i < docs.length(); i++) {
                            db.guardarMensajeLocal(docs.getJSONObject(i));
                        }
                        runOnUiThread(this::actualizarContadorBadge);
                    }
                }
            } catch (Exception e) {}
            
            // Re-verificar cada 10 segundos mientras la actividad esté viva
            if (!isFinishing()) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::verificarMensajesNuevos, 10000);
            }
        }).start();
    }

    private void actualizarContadorBadge() {
        if (farmaciaId == null || usuarioId == null || tvBadge == null) return;
        
        DBHelper db = new DBHelper(this);
        int noLeidos = db.contarNoLeidosCliente(farmaciaId, usuarioId);
        if (noLeidos > 0) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setText(String.valueOf(noLeidos));
        } else {
            tvBadge.setVisibility(View.GONE);
        }
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

    private void cargarMedicamentos() {
        if (farmaciaId == null) return;
        
        // 1. Mostrar carga y limpiar
        progress.setVisibility(View.VISIBLE);
        containerMed.removeAllViews();

        // 2. Carga inmediata desde cache
        DBHelper db = new DBHelper(this);
        List<JSONObject> cache = db.obtenerMedicamentosCache(null, false);
        JSONArray filtradosCache = new JSONArray();
        for (JSONObject med : cache) {
            if (med.optString("id_farmacia").equals(farmaciaId)) filtradosCache.put(med);
        }
        
        if (filtradosCache.length() > 0) {
            mostrarMedicamentos(filtradosCache);
            progress.setVisibility(View.GONE);
        }

        // 3. Actualización desde red
        new Thread(() -> {
            try {
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("id_farmacia", farmaciaId));
                    // Optimización: Solo traer campos necesarios
                    JSONArray fields = new JSONArray();
                    fields.put("_id"); fields.put("_rev"); fields.put("id_farmacia");
                    fields.put("nombre"); fields.put("precio"); fields.put("stock");
                    fields.put("presentacion"); fields.put("promocion");
                    fields.put("foto1"); fields.put("foto2"); fields.put("foto3");
                    fields.put("nombre_farmacia"); fields.put("enfermedad_objetivo");
                    selector.put("fields", fields);

                    String res = new TareaServidor().execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    JSONObject resJson = new JSONObject(res);
                    
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        for (int i = 0; i < docs.length(); i++) {
                            db.guardarMedicamentoLocal(docs.getJSONObject(i));
                        }
                        
                        // Recargar lista final
                        List<JSONObject> fresh = db.obtenerMedicamentosCache(null, false);
                        JSONArray filtradosFresh = new JSONArray();
                        for (JSONObject med : fresh) {
                            if (med.optString("id_farmacia").equals(farmaciaId)) filtradosFresh.put(med);
                        }

                        if (filtradosFresh.length() != filtradosCache.length()) {
                            runOnUiThread(() -> {
                                containerMed.removeAllViews();
                                mostrarMedicamentos(filtradosFresh);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("FarmaciaDetalle", "Error en red", e);
            } finally {
                runOnUiThread(() -> progress.setVisibility(View.GONE));
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
        TextView tvStock = card.findViewById(R.id.tvMedStock);
        Button btnAdd = card.findViewById(R.id.btnAgregarAlCarrito);

        tvNom.setText(med.getString("nombre"));
        tvFarm.setVisibility(View.GONE); // No es necesario decir el nombre de la farmacia porque ya estamos en su detalle
        tvPre.setText("$" + med.getString("precio"));
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
