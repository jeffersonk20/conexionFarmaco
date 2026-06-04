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
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class AdminFacturacionActivity extends AppCompatActivity {

    private LinearLayout containerReservas, containerPagosOnline;
    private Button btnVerReservas, btnVerPagos;
    private android.widget.ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_facturacion);

        containerReservas = findViewById(R.id.containerReservas);
        containerPagosOnline = findViewById(R.id.containerPagosOnline);
        btnVerReservas = findViewById(R.id.btnVerReservas);
        btnVerPagos = findViewById(R.id.btnVerPagos);
        progressBar = findViewById(R.id.pbAdminFact);
        
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
        // Solo cargar si no se hizo recientemente (opcional) o asegurar que no duplique
        cargarPedidos();
    }

    private void cargarPedidos() {
        String farmaciaId = getSharedPreferences("AdminPrefs", MODE_PRIVATE).getString("farmaciaId", "");
        if (farmaciaId.isEmpty()) return;

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                DBHelper db = new DBHelper(this);
                // 1. CARGA INMEDIATA DESDE CACHE (Para velocidad instantánea)
                List<JSONObject> cache = db.obtenerPedidosPorFarmacia(farmaciaId);
                JSONArray jsonCache = new JSONArray();
                for (JSONObject o : cache) jsonCache.put(o);
                
                final JSONArray finalJsonCache = jsonCache;
                runOnUiThread(() -> mostrarPedidos(finalJsonCache, farmaciaId));

                // 2. ACTUALIZACIÓN SILENCIOSA DESDE LA NUBE
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    JSONObject query = new JSONObject();
                    query.put("farmacias_ids", new JSONObject().put("$elemMatch", new JSONObject().put("$eq", farmaciaId)));
                    selector.put("selector", query);
                    selector.put("limit", 100);

                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_pedidos).get();
                    
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        db.limpiarPedidosFarmacia(farmaciaId);
                        for (int i = 0; i < docs.length(); i++) {
                            db.guardarPedidoLocal(docs.getJSONObject(i));
                        }
                        
                        // Recargar desde cache limpia para asegurar orden y sin duplicados
                        List<JSONObject> finalCache = db.obtenerPedidosPorFarmacia(farmaciaId);
                        JSONArray jsonFinal = new JSONArray();
                        for (JSONObject o : finalCache) jsonFinal.put(o);
                        
                        final JSONArray finalJsonFinal = jsonFinal;
                        runOnUiThread(() -> mostrarPedidos(finalJsonFinal, farmaciaId));
                    }
                }
            } catch (Exception e) {
                Log.e("AdminFact", "Error carga pedidos", e);
                runOnUiThread(() -> { if(progressBar != null) progressBar.setVisibility(View.GONE); });
            }
        }).start();
    }

    private void mostrarPedidos(JSONArray docs, String farmaciaId) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        containerReservas.removeAllViews();
        containerPagosOnline.removeAllViews();
        
        if (docs.length() == 0) return;

        // Set para evitar duplicados visuales por si acaso
        java.util.Set<String> idsProcesados = new java.util.HashSet<>();

        for (int i = 0; i < docs.length(); i++) {
            try {
                JSONObject pedidoFull = docs.getJSONObject(i);
                String id = pedidoFull.optString("_id");
                
                if (idsProcesados.contains(id)) continue;
                idsProcesados.add(id);
                
                // Filtrar los items para que el admin SOLO vea sus productos
                JSONObject pedidoFiltrado = new JSONObject(pedidoFull.toString());
                JSONArray itemsOriginales = pedidoFull.getJSONArray("items");
                JSONArray itemsMios = new JSONArray();
                double subtotalMio = 0;

                for (int j = 0; j < itemsOriginales.length(); j++) {
                    JSONObject item = itemsOriginales.getJSONObject(j);
                    if (item.optString("id_farmacia").equals(farmaciaId)) {
                        itemsMios.put(item);
                        double precio = item.optDouble("precio", 0);
                        int cant = item.optInt("cantidad", 1);
                        subtotalMio += (precio * cant);
                    }
                }

                if (itemsMios.length() > 0) {
                    pedidoFiltrado.put("items", itemsMios);
                    pedidoFiltrado.put("total_farmacia", subtotalMio);

                    String metodo = pedidoFull.optString("metodo_pago", "");
                    if (metodo.equalsIgnoreCase("efectivo")) {
                        agregarCardPedido(pedidoFiltrado, containerReservas);
                    } else {
                        agregarCardPedido(pedidoFiltrado, containerPagosOnline);
                    }
                }
            } catch (Exception e) {
                Log.e("AdminFact", "Error filtrando pedido", e);
            }
        }
    }


    private void agregarCardPedido(JSONObject pedido, LinearLayout container) throws Exception {
        View card = getLayoutInflater().inflate(R.layout.item_pedido_admin, container, false);
        
        TextView tvNombre = card.findViewById(R.id.tvPedidoCliente);
        TextView tvFecha = card.findViewById(R.id.tvPedidoFecha);
        TextView tvEstado = card.findViewById(R.id.tvPedidoEstado);
        TextView tvTotal = card.findViewById(R.id.tvPedidoTotal);
        TextView tvDetalle = card.findViewById(R.id.tvPedidoDetalleItems);
        Button btnGenerarFactura = card.findViewById(R.id.btnGenerarFactura);
        
        String cliente = pedido.optString("cliente_nombre", "");
        if (cliente.isEmpty() || cliente.equalsIgnoreCase("Desconocido")) {
            cliente = pedido.optString("cliente_correo", "Cliente Desconocido");
        }
        
        String fecha = pedido.optString("fecha", "");
        String estado = pedido.optString("estado", "Pendiente");
        double total = pedido.optDouble("total_farmacia", pedido.optDouble("total", 0));
        
        tvNombre.setText(cliente);
        tvFecha.setText(fecha);
        tvEstado.setText(estado);
        tvTotal.setText(String.format(java.util.Locale.US, "Total: $%.2f", total));
        
        JSONArray items = pedido.optJSONArray("items");
        StringBuilder resumenItems = new StringBuilder();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                resumenItems.append(it.optString("nombre", "Producto"))
                           .append(" (x").append(it.optInt("cantidad", 1)).append(")");
                if (i < items.length() - 1) resumenItems.append(", ");
            }
        }
        tvDetalle.setText(resumenItems.toString());
        
        if (btnGenerarFactura != null) {
            btnGenerarFactura.setOnClickListener(v -> enviarFactura(pedido));
        }

        final String nombreFinal = cliente;
        card.setOnClickListener(v -> {
            try {
                StringBuilder sb = new StringBuilder("DETALLE DE PEDIDO\n\n");
                sb.append("Cliente: ").append(nombreFinal).append("\n");
                sb.append("Tel: ").append(pedido.optString("cliente_telefono")).append("\n");
                sb.append("Dir: ").append(pedido.optString("cliente_direccion")).append("\n\n");
                sb.append("PRODUCTOS:\n");

                if (items != null) {
                    for(int i=0; i<items.length(); i++){
                        JSONObject it = items.getJSONObject(i);
                        sb.append("• ").append(it.getString("nombre"))
                          .append(" x").append(it.getInt("cantidad")).append("\n");
                    }
                }
                sb.append("\nTOTAL: $").append(String.format(java.util.Locale.US, "%.2f", total));
                Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {}
        });

        container.addView(card);
    }

    private void enviarFactura(JSONObject pedido) {
        try {
            String correoCliente = pedido.optString("cliente_correo", "");
            String nombreCliente = pedido.optString("cliente_nombre", "Cliente");
            String fecha = pedido.optString("fecha", "");
            JSONArray items = pedido.getJSONArray("items");
            double total = 0;

            if (correoCliente.isEmpty()) {
                Toast.makeText(this, "El cliente no tiene correo registrado", Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder html = new StringBuilder();
            html.append("<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; padding: 20px; border-radius: 10px; max-width: 600px;'>")
                .append("<h2 style='color: #1B4F72; text-align: center;'>FACTURA ELECTRÓNICA</h2>")
                .append("<p><strong>Cliente:</strong> ").append(nombreCliente).append("</p>")
                .append("<p><strong>Fecha:</strong> ").append(fecha).append("</p>")
                .append("<hr>")
                .append("<table style='width: 100%; border-collapse: collapse;'>")
                .append("<tr style='background: #f2f2f2;'><th style='text-align: left; padding: 8px;'>Producto</th><th style='padding: 8px;'>Cant.</th><th style='padding: 8px;'>Precio</th><th style='padding: 8px;'>Subtotal</th></tr>");

            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                double precio = it.optDouble("precio", 0);
                int cant = it.optInt("cantidad", 1);
                double subtotal = precio * cant;
                total += subtotal;

                html.append("<tr>")
                    .append("<td style='padding: 8px; border-bottom: 1px solid #eee;'>").append(it.getString("nombre")).append("</td>")
                    .append("<td style='padding: 8px; border-bottom: 1px solid #eee; text-align: center;'>").append(cant).append("</td>")
                    .append("<td style='padding: 8px; border-bottom: 1px solid #eee; text-align: center;'>$").append(String.format("%.2f", precio)).append("</td>")
                    .append("<td style='padding: 8px; border-bottom: 1px solid #eee; text-align: center;'>$").append(String.format("%.2f", subtotal)).append("</td>")
                    .append("</tr>");
            }

            html.append("</table>")
                .append("<h3 style='text-align: right; color: #1B4F72;'>TOTAL A PAGAR: $").append(String.format("%.2f", total)).append("</h3>")
                .append("<br><p style='font-size: 12px; color: #777;'>Gracias por su compra en Conexión Fármaco.</p>")
                .append("</div>");

            if (Utilidades.hayInternet(this)) {
                new MailManager(correoCliente, "🧾 Tu Factura - Conexión Fármaco", html.toString()).execute();
                Toast.makeText(this, "Factura enviada al correo del cliente", Toast.LENGTH_SHORT).show();
            } else {
                // Si no hay internet, guardar el envío del correo como pendiente
                JSONObject emailPendiente = new JSONObject();
                emailPendiente.put("destinatario", correoCliente);
                emailPendiente.put("asunto", "🧾 Tu Factura - Conexión Fármaco");
                emailPendiente.put("contenido", html.toString());
                
                new DBHelper(this).agregarPendiente("", "", emailPendiente.toString(), "email");
                Toast.makeText(this, "Factura guardada. Se enviará al conectar a internet.", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error al generar factura", Toast.LENGTH_SHORT).show();
            Log.e("AdminFact", "Error factura", e);
        }
    }
}