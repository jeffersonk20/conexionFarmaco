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
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("cliente_correo", userEmail));
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_pedidos).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        DBHelper db = new DBHelper(this);
                        for (int i = 0; i < docs.length(); i++) db.guardarPedidoLocal(docs.getJSONObject(i));
                        runOnUiThread(() -> mostrarHistorial(docs));
                        return;
                    }
                }
                // Cache si offline
                List<JSONObject> cache = new DBHelper(this).obtenerPedidosCache(userEmail);
                runOnUiThread(() -> mostrarHistorial(new JSONArray(cache)));
            } catch (Exception e) {
                Log.e("Historial", "Error carga", e);
            }
        }).start();
    }

    private void mostrarHistorial(JSONArray docs) {
        containerHistorial.removeAllViews();
        for (int i = 0; i < docs.length(); i++) {
            try {
                agregarCardHistorial(docs.getJSONObject(i));
            } catch (Exception e) {}
        }
    }


    private void agregarCardHistorial(JSONObject pedido) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_historial_cliente, containerHistorial, false);
        
        TextView tvFecha = card.findViewById(R.id.tvHistFecha);
        TextView tvEstado = card.findViewById(R.id.tvHistEstado);
        LinearLayout containerItems = card.findViewById(R.id.containerItemsHistorial);
        Button btnGuardar = card.findViewById(R.id.btnActualizarPedido);
        
        tvFecha.setText("Fecha: " + pedido.optString("fecha", ""));
        tvEstado.setText(pedido.optString("estado", "Pendiente"));
        
        JSONArray items = pedido.getJSONArray("items");
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
        card.findViewById(R.id.btnCancelarPedido).setOnClickListener(v -> cancelarPedido(pedido));

        containerHistorial.addView(card);
    }

    private void actualizarPedido(JSONObject pedido) {
        new Thread(() -> {
            try {
                // Para actualizar en CouchDB necesitamos la URL con el ID: /pedidos/id
                String urlUpdate = Utilidades.url_pedidos + "/" + pedido.getString("_id");
                
                TareaServidor tarea = new TareaServidor();
                // Usamos PUT para actualizar un documento específico
                String res = tarea.execute(pedido.toString(), "PUT", urlUpdate).get();
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Pedido actualizado con éxito", Toast.LENGTH_SHORT).show();
                    btnGuardarOcultar();
                    cargarHistorial();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void btnGuardarOcultar() {
        // Simple recarga para ocultar botones de guardar
    }

    private void cancelarPedido(JSONObject pedido) {
        new Thread(() -> {
            try {
                // Para eliminar en CouchDB enviamos el documento con _deleted: true via PUT al ID
                pedido.put("_deleted", true);
                String urlDelete = Utilidades.url_pedidos + "/" + pedido.getString("_id");
                
                TareaServidor tarea = new TareaServidor();
                tarea.execute(pedido.toString(), "PUT", urlDelete).get();
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Reserva cancelada con éxito", Toast.LENGTH_SHORT).show();
                    cargarHistorial();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error al cancelar", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}