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
        
        // Verificamos si venimos de un retorno de pago exitoso
        verificarRetornoPago(getIntent());

        findViewById(R.id.btnResAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnResCancelar).setOnClickListener(v -> finish());

        findViewById(R.id.btnFinalizarPedido).setOnClickListener(v -> {
            if (procesando) return;
            
            if (orderId == null) orderId = Utilidades.generarId();

            if ("tarjeta".equals(metodoPago)) {
                // Si el método es tarjeta, solo abrimos la pasarela.
                // NO guardamos el pedido todavía.
                Utilidades.pagarConWompi(this, totalCalculado, orderId);
                
                Toast.makeText(this, "Redirigiendo a pago seguro...", Toast.LENGTH_SHORT).show();
            } else {
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
        verificarRetornoPago(intent);
    }

    private void verificarRetornoPago(Intent intent) {
        if (intent != null && intent.getData() != null) {
            android.net.Uri data = intent.getData();
            String urlCompleta = data.toString();
            Log.d("Wompi", "URL de retorno recibida: " + urlCompleta);

            // Wompi SV devuelve 'esAprobada' como parámetro principal
            String aprobado = data.getQueryParameter("esAprobada");
            String idTransaccion = data.getQueryParameter("idTransaccion");
            
            // Detección flexible: 
            // 1. Si el parámetro es explícitamente true
            // 2. O si hay un id de transacción y no dice que falló
            boolean esExito = "true".equalsIgnoreCase(aprobado) || 
                             (idTransaccion != null && !urlCompleta.contains("false"));

            if (esExito && !procesando) {
                procesando = true;
                Toast.makeText(this, "¡Pago Confirmado! Registrando su pedido...", Toast.LENGTH_LONG).show();
                guardarPedido();
            } else if (!esExito) {
                Log.w("Wompi", "El pago no fue aprobado. Parámetros: " + urlCompleta);
                Toast.makeText(this, "El pago no se completó. Por favor intente de nuevo.", Toast.LENGTH_LONG).show();
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
                double precio = 0;
                try {
                    precio = Double.parseDouble(item.getString("precio"));
                } catch (Exception e) {
                    precio = item.optDouble("precio", 0);
                }
                subtotal += (precio * item.getInt("cantidad"));
            }

            double envio = 0; // Se elimina el cobro de envío
            totalCalculado = subtotal;

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

            if (orderId == null) orderId = Utilidades.generarId();

            JSONObject pedido = new JSONObject();
            pedido.put("_id", orderId);
            
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
            // Si es tarjeta y estamos en este punto, el pago ya se confirmó
            pedido.put("estado", metodoPago.equals("tarjeta") ? "Pagado" : "Pendiente");
            pedido.put("tipo_doc", "pedido"); // Para facilitar filtros


            // 1. GUARDADO LOCAL INMEDIATO (VELOCIDAD TOTAL)
            DBHelper dbHelper = new DBHelper(this);
            dbHelper.guardarPedidoLocal(pedido);

            // 2. Notificar éxito de inmediato y redirigir (Experiencia fluida)
            finalizarExitosamente(pedido, !Utilidades.hayInternet(this));

            // 3. Sincronizar en segundo plano sin bloquear al usuario
            new Thread(() -> {
                try {
                    if (Utilidades.hayInternet(this)) {
                        TareaServidor tarea = new TareaServidor();
                        String res = tarea.execute(pedido.toString(), "POST", Utilidades.url_pedidos).get();
                        
                        if (res == null || !res.contains("\"ok\":true")) {
                            // Si falló el envío directo, encolar para SyncWorker
                            dbHelper.agregarPendiente(Utilidades.url_pedidos, "POST", pedido.toString(), "couchdb");
                        }
                    } else {
                        dbHelper.agregarPendiente(Utilidades.url_pedidos, "POST", pedido.toString(), "couchdb");
                    }
                    Utilidades.sincronizar(this);
                } catch (Exception e) {
                    Log.e("ResumenPedido", "Error sync background", e);
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
                        (metodoPago.equals("efectivo") ? "¡Reserva realizada!" : "¡Redirigiendo a pago!");
            
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

            // Redirigimos siempre al Historial de Pedidos después de un éxito
            finishAffinity();
            startActivity(new Intent(this, HistorialPedidosActivity.class));
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
