package com.example.conexionfarmaco;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminFacturacionActivity extends AppCompatActivity {

    private LinearLayout containerReservas, containerPagosOnline;
    private Button btnVerReservas, btnVerPagos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_facturacion);

        containerReservas = findViewById(R.id.containerReservas);
        containerPagosOnline = findViewById(R.id.containerPagosOnline);
        btnVerReservas = findViewById(R.id.btnVerReservas);
        btnVerPagos = findViewById(R.id.btnVerPagos);
        
        findViewById(R.id.btnAdminFactAtras).setOnClickListener(v -> finish());

        btnVerReservas.setOnClickListener(v -> mostrarSeccion(true));
        btnVerPagos.setOnClickListener(v -> mostrarSeccion(false));

        cargarPedidos();
    }

    private void mostrarSeccion(boolean esReservas) {
        if (esReservas) {
            containerReservas.setVisibility(View.VISIBLE);
            containerPagosOnline.setVisibility(View.GONE);
            
            // Estilo botón activo
            btnVerReservas.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.azul_vibrante)));
            btnVerReservas.setTextColor(ContextCompat.getColor(this, R.color.white));
            
            // Estilo botón inactivo
            btnVerPagos.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.azul_suave)));
            btnVerPagos.setTextColor(ContextCompat.getColor(this, R.color.azul_primario));
        } else {
            containerReservas.setVisibility(View.GONE);
            containerPagosOnline.setVisibility(View.VISIBLE);
            
            // Estilo botón activo
            btnVerPagos.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.azul_vibrante)));
            btnVerPagos.setTextColor(ContextCompat.getColor(this, R.color.white));
            
            // Estilo botón inactivo
            btnVerReservas.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.azul_suave)));
            btnVerReservas.setTextColor(ContextCompat.getColor(this, R.color.azul_primario));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarPedidos();
    }

    private void cargarPedidos() {
        new Thread(() -> {
            try {
                JSONObject selector = new JSONObject();
                selector.put("selector", new JSONObject()); 

                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_pedidos).get();
                Log.d("AdminFact", "Respuesta pedidos: " + res);
                
                JSONObject resJson = new JSONObject(res);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    runOnUiThread(() -> {
                        containerReservas.removeAllViews();
                        containerPagosOnline.removeAllViews();
                        
                        if (docs.length() == 0) {
                            Toast.makeText(this, "No hay pedidos registrados", Toast.LENGTH_SHORT).show();
                        }

                        for (int i = 0; i < docs.length(); i++) {
                            try {
                                JSONObject pedido = docs.getJSONObject(i);
                                String tipo = pedido.optString("tipo", "");
                                if (tipo.equals("reserva")) {
                                    agregarCardPedido(pedido, containerReservas);
                                } else {
                                    agregarCardPedido(pedido, containerPagosOnline);
                                }
                            } catch (Exception e) {}
                        }
                    });
                } else if (resJson.has("error")) {
                    String error = resJson.optString("reason", "Error en DB");
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                Log.e("AdminFact", "Error carga pedidos", e);
            }
        }).start();
    }

    private void agregarCardPedido(JSONObject pedido, LinearLayout container) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_pedido_admin, container, false);
        
        TextView tvNombre = card.findViewById(R.id.tvPedidoCliente);
        TextView tvFecha = card.findViewById(R.id.tvPedidoFecha);
        TextView tvEstado = card.findViewById(R.id.tvPedidoEstado);
        
        String cliente = pedido.optString("cliente_nombre", "Desconocido");
        String fecha = pedido.optString("fecha", "");
        String estado = pedido.optString("estado", "Pendiente");
        
        tvNombre.setText(cliente);
        tvFecha.setText(fecha);
        tvEstado.setText(estado);
        
        card.setOnClickListener(v -> {
            try {
                JSONArray items = pedido.getJSONArray("items");
                StringBuilder sb = new StringBuilder("DETALLE DE PEDIDO\n\n");
                sb.append("Cliente: ").append(pedido.optString("cliente_nombre")).append("\n");
                sb.append("Tel: ").append(pedido.optString("cliente_telefono")).append("\n");
                sb.append("Dir: ").append(pedido.optString("cliente_direccion")).append("\n\n");
                sb.append("PRODUCTOS:\n");

                for(int i=0; i<items.length(); i++){
                    JSONObject it = items.getJSONObject(i);
                    sb.append("• ").append(it.getString("nombre"))
                      .append(" x").append(it.getInt("cantidad")).append("\n");
                }
                Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {}
        });

        container.addView(card);
    }
}