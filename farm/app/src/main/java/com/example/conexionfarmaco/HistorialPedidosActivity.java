package com.example.conexionfarmaco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class HistorialPedidosActivity extends AppCompatActivity {

    private LinearLayout containerHistorial;
    private String userEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial_pedidos);

        containerHistorial = findViewById(R.id.containerHistorial);
        findViewById(R.id.btnHistorialAtras).setOnClickListener(v -> finish());

        try {
            String userData = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userData", "");
            if (!userData.isEmpty()) {
                JSONObject user = new JSONObject(userData);
                userEmail = user.optString("correo", "");
            }
        } catch (Exception e) {}

        cargarHistorial();
    }

    private void cargarHistorial() {
        if (userEmail.isEmpty()) return;

        new Thread(() -> {
            try {
                DBHelper db = new DBHelper(this);
                
                // 1. Mostrar cache local de inmediato (VELOCIDAD)
                List<JSONObject> cacheInicial = db.obtenerPedidosCache(userEmail);
                runOnUiThread(() -> mostrarHistorial(new JSONArray(cacheInicial)));

                // 2. Si hay internet, sincronizar en segundo plano
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    JSONObject query = new JSONObject();
                    query.put("cliente_correo", userEmail);
                    query.put("tipo_doc", "pedido"); // Asegurar que solo traiga pedidos
                    selector.put("selector", query);

                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_pedidos).get();
                    
                    if (res != null && !res.contains("Error")) {
                        JSONObject resJson = new JSONObject(res);
                        if (resJson.has("docs")) {
                            JSONArray docs = resJson.getJSONArray("docs");
                            
                            // Guardar nuevos/actualizados sin borrar los locales pendientes
                            for (int i = 0; i < docs.length(); i++) {
                                db.guardarPedidoLocal(docs.getJSONObject(i));
                            }
                            
                            // Recargar lista final
                            List<JSONObject> cacheFinal = db.obtenerPedidosCache(userEmail);
                            runOnUiThread(() -> mostrarHistorial(new JSONArray(cacheFinal)));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("Historial", "Error carga", e);
            }
        }).start();
    }

    private void mostrarHistorial(JSONArray docs) {
        containerHistorial.removeAllViews();
        if (docs.length() == 0) return;

        // Carga progresiva para no congelar la UI
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        for (int i = 0; i < docs.length(); i++) {
            final int index = i;
            handler.postDelayed(() -> {
                try {
                    if (!isFinishing()) {
                        agregarCardHistorial(docs.getJSONObject(index));
                    }
                } catch (Exception e) {}
            }, (long) i * 50); // 50ms de retraso entre cada card
        }
    }


    private void agregarCardHistorial(JSONObject pedido) throws Exception {
        // Doble validación de seguridad local
        if (!pedido.optString("cliente_correo", "").equalsIgnoreCase(userEmail)) return;

        View card = getLayoutInflater().inflate(R.layout.item_historial_cliente, containerHistorial, false);
        
        TextView tvFecha = card.findViewById(R.id.tvHistFecha);
        TextView tvEstado = card.findViewById(R.id.tvHistEstado);
        LinearLayout containerItems = card.findViewById(R.id.containerItemsHistorial);
        Button btnGuardar = card.findViewById(R.id.btnActualizarPedido);
        Button btnRepetir = card.findViewById(R.id.btnRepetirPedido);
        
        String estado = pedido.optString("estado", "Pendiente");
        tvFecha.setText("Fecha: " + pedido.optString("fecha", ""));
        tvEstado.setText(estado);
        tvEstado.setTextColor(android.graphics.Color.parseColor(Utilidades.getEstadoPedidoColor(estado)));
        
        JSONArray items = pedido.getJSONArray("items");
        // ... (resto del código de carga de items)
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            View itemView = getLayoutInflater().inflate(R.layout.item_carrito, containerItems, false);
            
            itemView.findViewById(R.id.btnEliminarItem).setVisibility(View.GONE);
            
            TextView tvNom = itemView.findViewById(R.id.tvItemCarritoNombre);
            TextView tvCant = itemView.findViewById(R.id.tvItemCarritoCantidad);
            ImageView ivFoto = itemView.findViewById(R.id.ivItemCarritoFoto);
            
            tvNom.setText(item.getString("nombre"));
            tvCant.setText(String.valueOf(item.getInt("cantidad")));
            
            String foto = item.optString("foto1", "");
            if(!foto.isEmpty()) Utilidades.cargarImagenBase64(foto, ivFoto);

            itemView.findViewById(R.id.btnSumar).setOnClickListener(v -> {
                try {
                    int c = item.getInt("cantidad") + 1;
                    item.put("cantidad", c);
                    tvCant.setText(String.valueOf(c));
                    btnGuardar.setVisibility(View.VISIBLE);
                } catch (Exception e) {}
            });

            itemView.findViewById(R.id.btnRestar).setOnClickListener(v -> {
                try {
                    int c = item.getInt("cantidad");
                    if (c > 1) {
                        c--;
                        item.put("cantidad", c);
                        tvCant.setText(String.valueOf(c));
                        btnGuardar.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {}
            });

            containerItems.addView(itemView);
        }

        btnGuardar.setOnClickListener(v -> actualizarPedido(pedido));
        btnRepetir.setOnClickListener(v -> repetirPedido(pedido));
        card.findViewById(R.id.btnCancelarPedido).setOnClickListener(v -> cancelarPedido(pedido));

        containerHistorial.addView(card);
    }

    private void repetirPedido(JSONObject pedido) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("CartPrefs", MODE_PRIVATE);
            String cartStr = prefs.getString("cart", "[]");
            JSONArray cart = new JSONArray(cartStr);
            JSONArray itemsNuevos = pedido.getJSONArray("items");

            for (int i = 0; i < itemsNuevos.length(); i++) {
                JSONObject itemNuevo = itemsNuevos.getJSONObject(i);
                boolean existe = false;
                for (int j = 0; j < cart.length(); j++) {
                    if (cart.getJSONObject(j).getString("_id").equals(itemNuevo.getString("_id"))) {
                        cart.getJSONObject(j).put("cantidad", cart.getJSONObject(j).getInt("cantidad") + itemNuevo.getInt("cantidad"));
                        existe = true;
                        break;
                    }
                }
                if (!existe) cart.put(itemNuevo);
            }

            prefs.edit().putString("cart", cart.toString()).apply();
            Toast.makeText(this, "Productos agregados al carrito", Toast.LENGTH_SHORT).show();
            startActivity(new android.content.Intent(this, CarritoActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "Error al repetir pedido", Toast.LENGTH_SHORT).show();
        }
    }

    private void actualizarPedido(JSONObject pedido) {
        new Thread(() -> {
            try {
                String id = pedido.getString("_id");
                String urlUpdate = Utilidades.url_pedidos + "/" + id;
                DBHelper db = new DBHelper(this);

                // 1. Guardar localmente siempre
                db.guardarPedidoLocal(pedido);

                if (Utilidades.hayInternet(this)) {
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(pedido.toString(), "PUT", urlUpdate).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.optBoolean("ok", false)) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Pedido actualizado en la nube", Toast.LENGTH_SHORT).show();
                            cargarHistorial();
                        });
                        return;
                    }
                }

                // Si no hay internet o falló el servidor, encolar
                db.agregarPendiente(urlUpdate, "PUT", pedido.toString(), "couchdb");
                Utilidades.sincronizar(this);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Cambios guardados localmente. Sincronización pendiente.", Toast.LENGTH_SHORT).show();
                    cargarHistorial();
                });

            } catch (Exception e) {
                Log.e("Historial", "Error actualizando", e);
            }
        }).start();
    }

    private void btnGuardarOcultar() {
    }

    private void cancelarPedido(JSONObject pedido) {
        new Thread(() -> {
            try {
                String id = pedido.getString("_id");
                DBHelper db = new DBHelper(this);
                
                // 1. ELIMINAR LOCALMENTE SIEMPRE E INMEDIATAMENTE
                db.eliminarPedidoLocal(id);

                // 2. Refrescar la interfaz para que desaparezca YA
                runOnUiThread(() -> {
                    Toast.makeText(this, "Reserva cancelada localmente", Toast.LENGTH_SHORT).show();
                    cargarHistorial();
                });

                String urlDelete = Utilidades.url_pedidos + "/" + id + "?rev=" + pedido.optString("_rev", "");

                if (Utilidades.hayInternet(this)) {
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute("", "DELETE", urlDelete).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.optBoolean("ok", false)) {
                        Log.d("Historial", "Pedido eliminado en la nube");
                        return;
                    }
                }

                // 3b. Sin internet o falló el server: Encolar para el WorkManager
                db.agregarPendiente(urlDelete, "DELETE", "", "couchdb");
                Utilidades.sincronizar(this);
                Log.d("Historial", "Borrado encolado para sincronización posterior");
            } catch (Exception e) {
                Log.e("Historial", "Error al cancelar", e);
            }
        }).start();
    }
}