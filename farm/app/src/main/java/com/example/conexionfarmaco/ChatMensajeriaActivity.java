package com.example.conexionfarmaco;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMensajeriaActivity extends AppCompatActivity {

    private String idFarmacia, idUsuario, nombreReceptor, idPropio, nombrePropio, clienteNombreCompleto;
    private boolean esAdmin = false;
    private RecyclerView rvMensajes;
    private MensajeriaAdapter adapter;
    private List<JSONObject> listaMensajes = new ArrayList<>();
    private EditText etMensaje;
    private ImageButton btnEnviar;
    private DBHelper db;
    private android.widget.ProgressBar pb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_mensajeria);

        db = new DBHelper(this);
        rvMensajes = findViewById(R.id.rvMensajes);
        etMensaje = findViewById(R.id.etMensajeChat);
        btnEnviar = findViewById(R.id.btnEnviarChat);
        pb = findViewById(R.id.pbChat);
        TextView tvTitulo = findViewById(R.id.tvNombreReceptor);

        idFarmacia = getIntent().getStringExtra("id_farmacia");
        idUsuario = getIntent().getStringExtra("id_usuario");
        nombreReceptor = getIntent().getStringExtra("nombre_receptor");
        
        // Determinar si quien abre el chat es Admin (Farmacia) o Cliente
        String adminId = getSharedPreferences("AdminPrefs", MODE_PRIVATE).getString("farmaciaId", "");
        if (!adminId.isEmpty() && adminId.equals(idFarmacia)) {
            esAdmin = true;
            idPropio = idFarmacia;
            nombrePropio = getSharedPreferences("AdminPrefs", MODE_PRIVATE).getString("farmaciaNombre", "Farmacia");
            // El idUsuario ya viene en el intent cuando el admin abre el chat desde la lista
            clienteNombreCompleto = nombreReceptor; // Si soy admin, el receptor es el cliente
        } else {
            esAdmin = false;
            // Si es cliente, su ID está en UserPrefs
            try {
                JSONObject userData = new JSONObject(getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("userData", "{}"));
                idUsuario = userData.getString("_id");
                idPropio = idUsuario;
                // Concatenamos nombres y apellidos del cliente
                nombrePropio = (userData.optString("nombres", "") + " " + userData.optString("apellidos", "")).trim();
                if (nombrePropio.isEmpty()) nombrePropio = "Cliente";
                clienteNombreCompleto = nombrePropio;
            } catch (Exception e) { 
                idPropio = "anon"; 
                clienteNombreCompleto = "Cliente";
            }
        }

        tvTitulo.setText(nombreReceptor != null ? nombreReceptor : "Chat");

        adapter = new MensajeriaAdapter(listaMensajes, idPropio);
        rvMensajes.setLayoutManager(new LinearLayoutManager(this));
        rvMensajes.setAdapter(adapter);

        findViewById(R.id.btn_back_chat).setOnClickListener(v -> finish());
        btnEnviar.setOnClickListener(v -> enviarMensaje());

        cargarMensajes();
        iniciarAutoRecarga();
    }

    private void cargarMensajes() {
        // Solo mostrar cargando la primera vez
        if (listaMensajes.isEmpty()) pb.setVisibility(android.view.View.VISIBLE);
        
        new Thread(() -> {
            try {
                // Marcar como leídos al abrir el chat
                db.marcarComoLeido(idFarmacia, idUsuario, idPropio);

                // Primero cargar de local
                List<JSONObject> locales = db.obtenerMensajesChat(idFarmacia, idUsuario);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    listaMensajes.clear();
                    listaMensajes.addAll(locales);
                    adapter.notifyDataSetChanged();
                    rvMensajes.scrollToPosition(listaMensajes.size() - 1);
                });

                // Luego intentar sincronizar del servidor si hay internet
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    JSONObject query = new JSONObject();
                    query.put("id_farmacia", idFarmacia);
                    query.put("id_usuario", idUsuario);
                    query.put("tipo_doc", "mensaje");
                    selector.put("selector", query);

                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_mto + "/_find").get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        org.json.JSONArray docs = resJson.getJSONArray("docs");
                        for (int i = 0; i < docs.length(); i++) {
                            db.guardarMensajeLocal(docs.getJSONObject(i));
                        }
                        // Marcar de nuevo tras descargar nuevos
                        db.marcarComoLeido(idFarmacia, idUsuario, idPropio);
                        
                        // Recargar lista combinada
                        List<JSONObject> actualizados = db.obtenerMensajesChat(idFarmacia, idUsuario);
                        runOnUiThread(() -> {
                            listaMensajes.clear();
                            listaMensajes.addAll(actualizados);
                            adapter.notifyDataSetChanged();
                            rvMensajes.scrollToPosition(listaMensajes.size() - 1);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("Chat", "Error carga", e);
                runOnUiThread(() -> pb.setVisibility(android.view.View.GONE));
            }
        }).start();
    }

    private void enviarMensaje() {
        String texto = etMensaje.getText().toString().trim();
        if (texto.isEmpty()) return;

        try {
            JSONObject msg = new JSONObject();
            msg.put("_id", Utilidades.generarId());
            msg.put("emisor", idPropio);
            msg.put("receptor", esAdmin ? idUsuario : idFarmacia);
            msg.put("mensaje", texto);
            msg.put("fecha", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            msg.put("id_farmacia", idFarmacia);
            msg.put("id_usuario", idUsuario);
            msg.put("emisor_nombre", nombrePropio);
            msg.put("cliente_nombre_completo", clienteNombreCompleto);
            msg.put("tipo_doc", "mensaje");

            db.guardarMensajeLocal(msg);
            etMensaje.setText("");
            cargarMensajes(); // Refrescar UI

            new Thread(() -> {
                try {
                    if (Utilidades.hayInternet(this)) {
                        TareaServidor tarea = new TareaServidor();
                        tarea.execute(msg.toString(), "POST", Utilidades.url_mto).get();
                    } else {
                        db.agregarPendiente(Utilidades.url_mto, "POST", msg.toString(), "couchdb");
                    }
                } catch (Exception e) {}
            }).start();

        } catch (Exception e) {
            Toast.makeText(this, "Error al enviar", Toast.LENGTH_SHORT).show();
        }
    }

    private void iniciarAutoRecarga() {
        // En un app real usaríamos WebSockets o FCM, aquí simulamos con un polling cada 5 segundos
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    cargarMensajes();
                    iniciarAutoRecarga();
                }
            }
        }, 5000);
    }
}
