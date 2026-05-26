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
    private TextView tvStatPedidos, tvStatStockBajo;
    private ImageView ivLogo;
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
        tvStatPedidos = findViewById(R.id.tvStatPedidos);
        tvStatStockBajo = findViewById(R.id.tvStatStockBajo);
        ivLogo = findViewById(R.id.ivAdminHomeLogo);
        
        containerMedicamentos = findViewById(R.id.containerMedicamentosAdmin);
        etBuscador = findViewById(R.id.etAdminBuscadorMedicamento);
        
        SharedPreferences prefs = getSharedPreferences("AdminPrefs", MODE_PRIVATE);
        farmaciaId = prefs.getString("farmaciaId", "");
        tvNombreFarmacia.setText(prefs.getString("farmaciaNombre", "Mi Farmacia"));

        String fotoBase64 = prefs.getString("farmaciaFoto", "");
        if (!fotoBase64.isEmpty()) {
            Utilidades.cargarImagenBase64(fotoBase64, ivLogo);
        }

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

        ImageView ivChat = findViewById(R.id.ivAdminChat);
        if (prefs.getBoolean("chatHabilitado", false)) {
            ivChat.setVisibility(View.VISIBLE);
            ivChat.setOnClickListener(v -> {
                startActivity(new Intent(this, ListaChatClientesActivity.class));
            });
        }

        findViewById(R.id.cardStockBajo).setOnClickListener(v -> {
            filtrarStockBajo();
        });

        configurarBuscador();
    }

    private void filtrarStockBajo() {
        JSONArray filtrados = new JSONArray();
        for (int i = 0; i < listaOriginalMedicamentos.length(); i++) {
            try {
                JSONObject med = listaOriginalMedicamentos.getJSONObject(i);
                int stock = Integer.parseInt(med.optString("stock", "0"));
                if (stock < 5) filtrados.put(med);
            } catch (Exception e) {}
        }
        mostrarMedicamentos(filtrados);
        Toast.makeText(this, "Mostrando productos con stock bajo", Toast.LENGTH_SHORT).show();
    }

    private void actualizarDashboard() {
        DBHelper db = new DBHelper(this);
        
        // 1. Pedidos
        new Thread(() -> {
            try {
                List<JSONObject> pedidos = db.obtenerPedidosAdminCache();
                int count = 0;
                for (JSONObject p : pedidos) {
                    JSONArray ids = p.optJSONArray("farmacias_ids");
                    if (ids != null) {
                        for (int i = 0; i < ids.length(); i++) {
                            if (ids.getString(i).equals(farmaciaId)) {
                                count++;
                                break;
                            }
                        }
                    }
                }
                final int finalCount = count;
                runOnUiThread(() -> tvStatPedidos.setText(String.valueOf(finalCount)));
            } catch (Exception e) {}
        }).start();

        // 2. Stock Bajo (se calcula al cargar medicamentos)
        int stockBajo = 0;
        for (int i = 0; i < listaOriginalMedicamentos.length(); i++) {
            try {
                int stock = Integer.parseInt(listaOriginalMedicamentos.getJSONObject(i).optString("stock", "0"));
                if (stock < 5) stockBajo++;
            } catch (Exception e) {}
        }
        tvStatStockBajo.setText(String.valueOf(stockBajo));
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
            String normalizedQuery = Utilidades.normalizar(query);
            for (int i = 0; i < listaOriginalMedicamentos.length(); i++) {
                try {
                    JSONObject med = listaOriginalMedicamentos.getJSONObject(i);
                    String nom = Utilidades.normalizar(med.getString("nombre"));
                    String enf = Utilidades.normalizar(med.optString("enfermedad_objetivo", ""));
                    if (nom.contains(normalizedQuery) || enf.contains(normalizedQuery)) {
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
        actualizarDashboard();
    }

    private void cargarMedicamentos() {
        if (farmaciaId.isEmpty()) return;

        new Thread(() -> {
            try {
                DBHelper db = new DBHelper(this);
                
                // 1. Mostrar siempre lo que hay en cache primero
                List<JSONObject> cache = db.obtenerMedicamentosCache(null, false);
                listaOriginalMedicamentos = new JSONArray();
                for (JSONObject m : cache) {
                    if (m.optString("id_farmacia").equals(farmaciaId)) listaOriginalMedicamentos.put(m);
                }
                actualizarListaAdmin();
                runOnUiThread(this::actualizarDashboard);

                // 2. Si hay internet, intentar actualizar
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("id_farmacia", farmaciaId));
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_medicamentos).get();
                    JSONObject resJson = new JSONObject(res);
                    
                    if (resJson.has("docs")) {
                        JSONArray docsServidor = resJson.getJSONArray("docs");
                        
                        // 1. Identificar qué estamos haciendo offline para no borrarlo
                        List<JSONObject> pendientes = db.obtenerPendientes();
                        java.util.Set<String> idsEliminando = new java.util.HashSet<>();
                        java.util.Set<String> idsEditando = new java.util.HashSet<>();
                        java.util.Set<String> idsAgregando = new java.util.HashSet<>();
                        
                        for (JSONObject p : pendientes) {
                            String pUrl = p.optString("url", "");
                            String pMetodo = p.optString("metodo", "");
                            if (!pUrl.contains("medicamentos")) continue;
                            
                            String pId = "";
                            if (pMetodo.equals("POST")) {
                                try { pId = new JSONObject(p.getString("json")).optString("_id"); } catch(Exception e){}
                                if (!pId.isEmpty()) idsAgregando.add(pId);
                            } else {
                                pId = pUrl.substring(pUrl.lastIndexOf("/") + 1);
                                if (pId.contains("?")) pId = pId.substring(0, pId.indexOf("?"));
                                if (pMetodo.equals("DELETE")) idsEliminando.add(pId);
                                else if (pMetodo.equals("PUT")) idsEditando.add(pId);
                            }
                        }

                        // 2. Guardar lo nuevo del servidor
                        java.util.Set<String> idsEnServidor = new java.util.HashSet<>();
                        for (int i = 0; i < docsServidor.length(); i++) {
                            JSONObject doc = docsServidor.getJSONObject(i);
                            String idS = doc.getString("_id");
                            idsEnServidor.add(idS);
                            
                            if (!idsEliminando.contains(idS) && !idsEditando.contains(idS)) {
                                db.guardarMedicamentoLocal(doc);
                            }
                        }

                        // 3. PURGA: Eliminar del cache local lo que NO está en el servidor 
                        // Y que no sea algo que acabamos de crear offline
                        List<JSONObject> cacheLocal = db.obtenerMedicamentosCache(null, false);
                        for (JSONObject m : cacheLocal) {
                            if (m.optString("id_farmacia").equals(farmaciaId)) {
                                String idL = m.optString("_id");
                                if (!idsEnServidor.contains(idL) && !idsAgregando.contains(idL)) {
                                    db.eliminarMedicamentoLocal(idL);
                                }
                            }
                        }

                        // 4. Refrescar UI combinada
                        List<JSONObject> finalCache = db.obtenerMedicamentosCache(null, false);
                        listaOriginalMedicamentos = new JSONArray();
                        for (JSONObject m : finalCache) {
                            if (m.optString("id_farmacia").equals(farmaciaId)) {
                                listaOriginalMedicamentos.put(m);
                            }
                        }

                        actualizarListaAdmin();
                        runOnUiThread(this::actualizarDashboard);
                    }
                }
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
