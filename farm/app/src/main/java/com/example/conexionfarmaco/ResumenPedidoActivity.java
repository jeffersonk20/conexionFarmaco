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
    private boolean procesando = false;
    private String orderId = null;

    // Campos para persistir datos de facturación ante recreación de actividad (Wompi)
    private String nombreFact = "", direccionFact = "", telFact = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resumen_pedido);

        // Capturar datos del Intent original (FacturacionActivity)
        if (getIntent().hasExtra("metodo_pago")) {
            metodoPago = getIntent().getStringExtra("metodo_pago");
            nombreFact = getIntent().getStringExtra("fact_nombre");
            direccionFact = getIntent().getStringExtra("fact_direccion");
            telFact = getIntent().getStringExtra("fact_tel");
        }

        if (metodoPago == null) metodoPago = "tarjeta";

        tvSubtotal = findViewById(R.id.tvResSubtotal);
        tvEnvio = findViewById(R.id.tvResEnvio);
        tvTotal = findViewById(R.id.tvResTotal);

        TextView tvMetodo = findViewById(R.id.tvResMetodoPago);
        if ("efectivo".equals(metodoPago)) {
            tvMetodo.setText("Efectivo (Reserva)");
        } else {
            tvMetodo.setText("Tarjeta de Crédito/Débito");
        }

        cargarDatosUsuario();
        calcularResumen();
        
        // Verificamos si venimos de un retorno de pago exitoso (Deep Link)
        verificarRetornoPago(getIntent());

        findViewById(R.id.btnResAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnResCancelar).setOnClickListener(v -> finish());

        findViewById(R.id.btnFinalizarPedido).setOnClickListener(v -> {
            if (procesando) return;
            
            if (orderId == null) orderId = Utilidades.generarId();

            if ("tarjeta".equals(metodoPago)) {
                // Si el método es tarjeta, solo abrimos la pasarela.
                // NO guardamos el pedido localmente todavía para evitar duplicados "pendientes"
                Utilidades.pagarConWompi(this, totalCalculado, orderId);
                Toast.makeText(this, "Conectando con pasarela segura...", Toast.LENGTH_SHORT).show();
            } else {
                // Para efectivo, guardamos de inmediato
                procesando = true;
                v.setEnabled(false);
                guardarPedido();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Al regresar de Wompi, el intent nuevo NO trae los extras de facturación,
        // por eso usamos las variables de clase nombreFact, direccionFact, etc.
        verificarRetornoPago(intent);
    }

    private void verificarRetornoPago(Intent intent) {
        if (intent != null && intent.getData() != null) {
            android.net.Uri data = intent.getData();
            String urlCompleta = data.toString();
            Log.d("Wompi", "URL de retorno recibida: " + urlCompleta);

            String aprobado = data.getQueryParameter("esAprobada");
            String idTransaccion = data.getQueryParameter("idTransaccion");
            
            boolean esExito = "true".equalsIgnoreCase(aprobado) || 
                             (idTransaccion != null && !urlCompleta.contains("false"));

            if (esExito && !procesando) {
                // Si el pago es exitoso, forzamos que el método sea tarjeta (por si acaso)
                metodoPago = "tarjeta";
                procesando = true;
                Toast.makeText(this, "¡Pago Confirmado! Registrando su pedido...", Toast.LENGTH_LONG).show();
                guardarPedido();
            } else if (!esExito) {
                Log.w("Wompi", "El pago no fue aprobado.");
                Toast.makeText(this, "El pago no se completó. Intente de nuevo.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void calcularResumen() {
        try {
            String cartStr = getSharedPreferences("CartPrefs", MODE_PRIVATE).getString("cart", "[]");
            JSONArray cartItems = new JSONArray(cartStr);
            double subtotal = 0;

            for (int i = 0; i < cartItems.length(); i++) {
                JSONObject item = cartItems.getJSONObject(i);
                double precio = item.optDouble("precio", 0);
                subtotal += (precio * item.getInt("cantidad"));
            }

            totalCalculado = subtotal;
            tvSubtotal.setText(String.format(java.util.Locale.US, "US$ %.2f", subtotal));
            tvEnvio.setText("US$ 0.00");
            tvTotal.setText(String.format(java.util.Locale.US, "US$ %.2f", totalCalculado));

        } catch (Exception e) {
            Log.e("ResumenPedido", "Error calculando total", e);
        }
    }

    private void guardarPedido() {
        try {
            String cartStr = getSharedPreferences("CartPrefs", MODE_PRIVATE).getString("cart", "[]");
            JSONArray cartItems = new JSONArray(cartStr);
            
            if (cartItems.length() == 0) return;

            if (orderId == null) orderId = Utilidades.generarId();

            JSONObject pedido = new JSONObject();
            pedido.put("_id", orderId);
            
            // Usar variables persistentes para evitar datos NULL al volver de la pasarela
            pedido.put("cliente_nombre", (nombreFact == null || nombreFact.isEmpty()) ? userName : nombreFact);
            pedido.put("cliente_direccion", (direccionFact == null) ? "" : direccionFact);
            pedido.put("cliente_telefono", (telFact == null) ? "" : telFact);
            pedido.put("cliente_correo", userEmail);

            pedido.put("items", cartItems);
            pedido.put("total", String.valueOf(totalCalculado));
            
            JSONArray farmaciasInvolucradas = new JSONArray();
            java.util.HashSet<String> farmIds = new java.util.HashSet<>();
            for (int i = 0; i < cartItems.length(); i++) {
                String fId = cartItems.getJSONObject(i).optString("id_farmacia");
                if (!fId.isEmpty()) farmIds.add(fId);
            }
            for (String idF : farmIds) farmaciasInvolucradas.put(idF);
            pedido.put("farmacias_ids", farmaciasInvolucradas);

            // IMPORTANTE: Asegurar que el método y tipo sean correctos
            pedido.put("metodo_pago", metodoPago);
            pedido.put("tipo", "efectivo".equals(metodoPago) ? "reserva" : "pago_online");
            pedido.put("fecha", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
            pedido.put("estado", "tarjeta".equals(metodoPago) ? "Pagado" : "Pendiente");
            pedido.put("tipo_doc", "pedido");

            // 1. Guardado local
            DBHelper dbHelper = new DBHelper(this);
            dbHelper.guardarPedidoLocal(pedido);

            // 2. Descontar stock
            Utilidades.descontarStock(this, cartItems);

            // 3. Notificar y salir
            finalizarExitosamente(pedido, !Utilidades.hayInternet(this));

            // 4. Sincronizar en segundo plano
            new Thread(() -> {
                try {
                    if (Utilidades.hayInternet(this)) {
                        TareaServidor tarea = new TareaServidor();
                        String res = tarea.execute(pedido.toString(), "POST", Utilidades.url_pedidos).get();
                        if (res == null || !res.contains("\"ok\":true")) {
                            dbHelper.agregarPendiente(Utilidades.url_pedidos, "POST", pedido.toString(), "couchdb");
                        }
                    } else {
                        dbHelper.agregarPendiente(Utilidades.url_pedidos, "POST", pedido.toString(), "couchdb");
                    }
                    Utilidades.sincronizar(this);
                } catch (Exception e) {}
            }).start();

        } catch (Exception e) {
            Log.e("ResumenPedido", "Error al guardar", e);
            Toast.makeText(this, "Error al procesar el pedido", Toast.LENGTH_SHORT).show();
        }
    }

    private void finalizarExitosamente(JSONObject pedido, boolean wasOffline) {
        runOnUiThread(() -> {
            // Guardar correo como pendiente si estamos offline o enviarlo ahora
            if (wasOffline) {
                try {
                    String titulo = "efectivo".equals(metodoPago) ? "📝 Confirmación de Reserva" : "📦 Confirmación de Pedido";
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
                        ("efectivo".equals(metodoPago) ? "¡Reserva realizada!" : "¡Pago completado con éxito!");
            
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

            // Redirigimos siempre al Historial de Pedidos después de un éxito
            // Eliminamos finishAffinity() para que el botón "Atrás" funcione correctamente
            Intent intent = new Intent(this, HistorialPedidosActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // Limpiar actividades intermedias como Facturacion
            startActivity(intent);
            finish(); // Finalizar ResumenPedidoActivity
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

        String titulo = "efectivo".equals(metodoPago) ? "📝 Confirmación de Reserva" : "📦 Confirmación de Pedido";
        String detallePago = "efectivo".equals(metodoPago) ? "Pago en efectivo al recibir" : "Pago procesado con tarjeta";

        String subject = titulo + " - Conexión Fármaco!";
        String content = "<div style='font-family: Arial, sans-serif; color: #2E4053; border: 1px solid #D6EAF8; padding: 20px; border-radius: 10px;'>" +
                "<h2 style='color: #1B4F72;'>¡Hola, " + userName + "!</h2>" +
                "<p>Tu " + ("efectivo".equals(metodoPago) ? "reserva" : "pedido") + " ha sido procesado exitosamente.</p>" +
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
