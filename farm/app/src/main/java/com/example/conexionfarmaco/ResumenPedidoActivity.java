package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class ResumenPedidoActivity extends AppCompatActivity {

    private String userEmail = "", userName = "";
    private String metodoPago = "tarjeta";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resumen_pedido);

        metodoPago = getIntent().getStringExtra("metodo_pago");
        if (metodoPago == null) metodoPago = "tarjeta";

        TextView tvMetodo = findViewById(R.id.tvResMetodoPago);
        if (metodoPago.equals("efectivo")) {
            tvMetodo.setText("Efectivo (Reserva)");
        } else {
            tvMetodo.setText("Tarjeta de Crédito/Débito");
        }

        cargarDatosUsuario();

        findViewById(R.id.btnResAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnResCancelar).setOnClickListener(v -> finish());

        findViewById(R.id.btnFinalizarPedido).setOnClickListener(v -> {
            guardarPedido();
        });
    }

    private void guardarPedido() {
        try {
            String cartStr = getSharedPreferences("CartPrefs", MODE_PRIVATE).getString("cart", "[]");
            JSONArray cartItems = new JSONArray(cartStr);
            
            if (cartItems.length() == 0) {
                Toast.makeText(this, "El carrito está vacío", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject pedido = new JSONObject();
            pedido.put("_id", Utilidades.generarId());
            
            // Datos de facturación/envío
            pedido.put("cliente_nombre", getIntent().getStringExtra("fact_nombre"));
            pedido.put("cliente_direccion", getIntent().getStringExtra("fact_direccion"));
            pedido.put("cliente_telefono", getIntent().getStringExtra("fact_tel"));
            pedido.put("cliente_correo", userEmail);

            pedido.put("items", cartItems);
            pedido.put("metodo_pago", metodoPago);
            pedido.put("tipo", metodoPago.equals("efectivo") ? "reserva" : "pago_online");
            pedido.put("fecha", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
            pedido.put("estado", "Pendiente");

            new Thread(() -> {
                try {
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(pedido.toString(), "POST", Utilidades.url_pedidos).get();
                    Log.d("ResumenPedido", "Respuesta servidor: " + res);
                    
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.optBoolean("ok", false)) {
                        runOnUiThread(() -> {
                            enviarNotificacionPedido();
                            getSharedPreferences("CartPrefs", MODE_PRIVATE).edit().putString("cart", "[]").apply();
                            
                            String msg = metodoPago.equals("efectivo") ? "¡Reserva realizada! Paga al recibir." : "¡Pedido finalizado con éxito!";
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            
                            finishAffinity();
                            startActivity(new Intent(this, HomeActivity.class));
                        });
                    } else {
                        runOnUiThread(() -> {
                            String error = resJson.optString("reason", "Error desconocido");
                            Toast.makeText(this, "Error al guardar: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                } catch (Exception e) {
                    Log.e("ResumenPedido", "Error al procesar", e);
                    runOnUiThread(() -> Toast.makeText(this, "Error de conexión con el servidor", Toast.LENGTH_SHORT).show());
                }
            }).start();

        } catch (Exception e) {
            Toast.makeText(this, "Error local", Toast.LENGTH_SHORT).show();
        }
    }

    private void cargarDatosUsuario() {
        try {
            String userData = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userData", "");
            if (!userData.isEmpty()) {
                JSONObject user = new JSONObject(userData);
                userEmail = user.optString("correo", "");
                userName = user.optString("nombres", "Usuario");
            }
        } catch (Exception e) {
            Log.e("ResumenPedido", "Error al cargar datos", e);
        }
    }

    private void enviarNotificacionPedido() {
        if (userEmail.isEmpty()) return;

        String titulo = metodoPago.equals("efectivo") ? "📝 Confirmación de Reserva" : "📦 Confirmación de Pedido";
        String detallePago = metodoPago.equals("efectivo") ? "Pago en efectivo al recibir" : "Pago procesado con tarjeta";

        String subject = titulo + " - Conexión Fármaco!";
        String content = "<div style='font-family: Arial, sans-serif; color: #2E4053; border: 1px solid #D6EAF8; padding: 20px; border-radius: 10px;'>" +
                "<h2 style='color: #1B4F72;'>¡Hola, " + userName + "!</h2>" +
                "<p>Tu " + (metodoPago.equals("efectivo") ? "reserva" : "pedido") + " ha sido procesado exitosamente.</p>" +
                "<p><strong>Detalles:</strong></p>" +
                "<ul>" +
                "<li><strong>Método de pago:</strong> " + detallePago + "</li>" +
                "<li><strong>Estado:</strong> En preparación</li>" +
                "<li><strong>Entrega estimada:</strong> 30-60 min</li>" +
                "</ul>" +
                "<p>Gracias por confiar en nosotros para cuidar de tu salud.</p>" +
                "<br><hr style='border: 0; border-top: 1px solid #D6EAF8;'>" +
                "<p style='font-size: 12px; color: #5DADE2;'>Este es un mensaje automático de Conexión Fármaco.</p>" +
                "</div>";

        new MailManager(userEmail, subject, content).execute();
    }
}
