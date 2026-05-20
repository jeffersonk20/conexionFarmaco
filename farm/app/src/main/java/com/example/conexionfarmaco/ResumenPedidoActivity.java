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
    private TextView tvSubtotal, tvEnvio, tvTotal;
    private double totalCalculado = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resumen_pedido);

        metodoPago = getIntent().getStringExtra("metodo_pago");
        if (metodoPago == null) metodoPago = "tarjeta";

        tvSubtotal = findViewById(R.id.tvResSubtotal);
        tvEnvio = findViewById(R.id.tvResEnvio);
        tvTotal = findViewById(R.id.tvResTotal);

        TextView tvMetodo = findViewById(R.id.tvResMetodoPago);
        if (metodoPago.equals("efectivo")) {
            tvMetodo.setText("Efectivo (Reserva)");
        } else {
            tvMetodo.setText("Tarjeta de Crédito/Débito");
        }

        cargarDatosUsuario();
        calcularResumen();

        findViewById(R.id.btnResAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnResCancelar).setOnClickListener(v -> finish());

        findViewById(R.id.btnFinalizarPedido).setOnClickListener(v -> {
            guardarPedido();
        });
    }

    private void calcularResumen() {
        try {
            String cartStr = getSharedPreferences("CartPrefs", MODE_PRIVATE).getString("cart", "[]");
            JSONArray cartItems = new JSONArray(cartStr);
            double subtotal = 0;

            for (int i = 0; i < cartItems.length(); i++) {
                JSONObject item = cartItems.getJSONObject(i);
                double precio = 0;
                try {
                    precio = Double.parseDouble(item.getString("precio"));
                } catch (Exception e) {
                    precio = item.optDouble("precio", 0);
                }
                subtotal += (precio * item.getInt("cantidad"));
            }

            double envio = subtotal > 0 ? 5.00 : 0; // Tarifa fija de envío para el ejemplo
            totalCalculado = subtotal + envio;

            tvSubtotal.setText(String.format(java.util.Locale.US, "US$ %.2f", subtotal));
            tvEnvio.setText(String.format(java.util.Locale.US, "US$ %.2f", envio));
            tvTotal.setText(String.format(java.util.Locale.US, "US$ %.2f", totalCalculado));

        } catch (Exception e) {
            Log.e("ResumenPedido", "Error calculando total", e);
        }
    }

    private void guardarPedido() {
        try {
            String cartStr = getSharedPreferences("CartPrefs", MODE_PRIVATE).getString("cart", "[]");
            JSONArray cartItems = new JSONArray(cartStr);
            
            if (cartItems.length() == 0) {
                Toast.makeText(this, "El carrito está vacío", Toast.LENGTH_SHORT).show();
                return;
            }

            String nombreFact = getIntent().getStringExtra("fact_nombre");
            if (nombreFact == null || nombreFact.isEmpty()) {
                nombreFact = userName; // Fallback al nombre del usuario si no hay nombre de facturación
            }

            JSONObject pedido = new JSONObject();
            pedido.put("_id", Utilidades.generarId());
            
            // Datos de facturación/envío
            pedido.put("cliente_nombre", nombreFact);
            pedido.put("cliente_direccion", getIntent().getStringExtra("fact_direccion"));
            pedido.put("cliente_telefono", getIntent().getStringExtra("fact_tel"));
            pedido.put("cliente_correo", userEmail);

            pedido.put("items", cartItems);
            pedido.put("total", String.valueOf(totalCalculado));
            
            // Extraer IDs de farmacias involucradas para que los admin puedan filtrar
            JSONArray farmaciasInvolucradas = new JSONArray();
            java.util.HashSet<String> farmIds = new java.util.HashSet<>();
            for (int i = 0; i < cartItems.length(); i++) {
                String fId = cartItems.getJSONObject(i).optString("id_farmacia");
                if (!fId.isEmpty()) farmIds.add(fId);
            }
            for (String idF : farmIds) farmaciasInvolucradas.put(idF);
            pedido.put("farmacias_ids", farmaciasInvolucradas);

            pedido.put("metodo_pago", metodoPago);
            pedido.put("tipo", metodoPago.equals("efectivo") ? "reserva" : "pago_online");
            pedido.put("fecha", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
            pedido.put("estado", "Pendiente");
            pedido.put("tipo_doc", "pedido"); // Para facilitar filtros


            new Thread(() -> {
                try {
                    DBHelper dbHelper = new DBHelper(this);
                    // GUARDADO LOCAL SIEMPRE (para que aparezca en historial de inmediato)
                    dbHelper.guardarPedidoLocal(pedido);

                    if (Utilidades.hayInternet(this)) {
                        TareaServidor tarea = new TareaServidor();
                        String res = tarea.execute(pedido.toString(), "POST", Utilidades.url_pedidos).get();
                        JSONObject resJson = new JSONObject(res);

                        if (resJson.optBoolean("ok", false)) {
                            finalizarExitosamente(pedido, false);
                            return;
                        }
                    }

                    // Si no hay internet o falló el server, ya está en pedidos local,
                    // pero necesitamos que se sincronice luego
                    dbHelper.agregarPendiente(Utilidades.url_pedidos, "POST", pedido.toString(), "couchdb");
                    Utilidades.sincronizar(this);
                    finalizarExitosamente(pedido, true);

                } catch (Exception e) {
                    Log.e("ResumenPedido", "Error al procesar", e);
                }
            }).start();



        } catch (Exception e) {
            Toast.makeText(this, "Error local", Toast.LENGTH_SHORT).show();
        }
    }


    private void finalizarExitosamente(JSONObject pedido, boolean wasOffline) {
        runOnUiThread(() -> {
            // Guardar correo como pendiente si estamos offline o enviarlo ahora
            if (wasOffline) {
                try {
                    String titulo = metodoPago.equals("efectivo") ? "📝 Confirmación de Reserva" : "📦 Confirmación de Pedido";
                    String detallePago = metodoPago.equals("efectivo") ? "Pago en efectivo al recibir" : "Pago procesado con tarjeta";
                    String subject = titulo + " - Conexión Fármaco!";
                    String content = "Hola " + userName + ", tu pedido se ha registrado offline y se enviará al conectar.";

                    JSONObject emailData = new JSONObject();
                    emailData.put("destinatario", userEmail);
                    emailData.put("asunto", subject);
                    emailData.put("contenido", content);

                    new DBHelper(this).agregarPendiente("", "", emailData.toString(), "email");
                } catch (Exception e) {}
            } else {
                enviarNotificacionPedido();
            }

            getSharedPreferences("CartPrefs", MODE_PRIVATE).edit().putString("cart", "[]").apply();
            String msg = wasOffline ? "Guardado localmente. Se sincronizará al tener internet." : 
                        (metodoPago.equals("efectivo") ? "¡Reserva realizada!" : "¡Pedido finalizado!");
            
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finishAffinity();
            startActivity(new Intent(this, HomeActivity.class));
        });
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
