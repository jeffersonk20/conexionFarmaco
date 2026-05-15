package com.example.conexionfarmaco;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminPerfilActivity extends AppCompatActivity {

    private ImageView imgLogo;
    private TextView tvNombre, tvDireccion, tvTelefono, tvCorreo, tvDesc;
    private Button btnCerrarSesion;
    private String farmaciaId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_perfil);

        imgLogo = findViewById(R.id.imgAdminPerfilLogo);
        tvNombre = findViewById(R.id.tvAdminPerfilNombre);
        tvDireccion = findViewById(R.id.tvAdminPerfilDireccion);
        tvTelefono = findViewById(R.id.tvAdminPerfilTelefono);
        tvCorreo = findViewById(R.id.tvAdminPerfilCorreo);
        tvDesc = findViewById(R.id.tvAdminPerfilDesc);
        btnCerrarSesion = findViewById(R.id.btnAdminPerfilCerrarSesion);

        findViewById(R.id.toolbarAdminPerfil).setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences("AdminPrefs", MODE_PRIVATE);
        farmaciaId = prefs.getString("farmaciaId", "");

        cargarDatosFarmacia();

        btnCerrarSesion.setOnClickListener(v -> cerrarSesion());
    }

    private void cargarDatosFarmacia() {
        if (farmaciaId.isEmpty()) return;

        // Primero intentar cargar de la base de datos local (SQLite)
        DBHelper db = new DBHelper(this);
        JSONObject localFarm = db.obtenerFarmaciaLocal(farmaciaId);
        if (localFarm != null) {
            actualizarUI(localFarm);
        }

        // Luego intentar actualizar desde la nube si hay internet
        if (Utilidades.hayInternet(this)) {
            new Thread(() -> {
                try {
                    JSONObject selector = new JSONObject();
                    selector.put("selector", new JSONObject().put("_id", farmaciaId));
                    
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                    
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        JSONArray docs = resJson.getJSONArray("docs");
                        if (docs.length() > 0) {
                            JSONObject farm = docs.getJSONObject(0);
                            
                            // Actualizar SQLite con lo más reciente de la nube
                            db.administrarFarmacias("modificar", new String[]{
                                    farm.getString("_id"),
                                    farm.getString("empresa"),
                                    farm.optString("direccion", ""),
                                    farm.optString("telefono", ""),
                                    farm.optString("correo", ""),
                                    farm.optString("clave", ""),
                                    farm.optString("foto", ""),
                                    farm.optString("descripcion", "")
                            });

                            runOnUiThread(() -> actualizarUI(farm));
                        }
                    }
                } catch (Exception e) {
                    Log.e("AdminPerfil", "Error red", e);
                }
            }).start();
        }
    }

    private void actualizarUI(JSONObject farm) {
        try {
            tvNombre.setText(farm.getString("empresa"));
            tvDireccion.setText(farm.optString("direccion", "No especificada"));
            tvTelefono.setText(farm.optString("telefono", "No disponible"));
            tvCorreo.setText(farm.optString("correo", "Sin correo"));
            tvDesc.setText(farm.optString("descripcion", "Sin descripción"));

            String foto = farm.optString("foto", "");
            if (!foto.isEmpty()) {
                if (foto.startsWith("/") || foto.startsWith("content://") || foto.contains("storage")) {
                    imgLogo.setImageURI(Uri.parse(foto));
                } else {
                    Utilidades.cargarImagenBase64(foto, imgLogo);
                }
            }
        } catch (Exception e) {
            Log.e("AdminPerfil", "Error UI", e);
        }
    }


    private void cerrarSesion() {
        getSharedPreferences("AdminPrefs", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
