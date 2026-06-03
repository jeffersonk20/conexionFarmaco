package com.example.conexionfarmaco;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminLoginActivity extends AppCompatActivity {

    private EditText etCorreo, etPass;
    private Button btnIngresar, btnAtras;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        etCorreo = findViewById(R.id.etAdminLoginCorreo);
        etPass = findViewById(R.id.etAdminLoginPass);
        btnIngresar = findViewById(R.id.btnAdminIngresar);
        btnAtras = findViewById(R.id.btnAdminLoginAtras);

        btnIngresar.setOnClickListener(v -> loginFarmacia());
        btnAtras.setOnClickListener(v -> finish());
    }

    private void loginFarmacia() {
        String cor = etCorreo.getText().toString().trim();
        String cla = etPass.getText().toString().trim();

        if (cor.isEmpty() || cla.isEmpty()) {
            Toast.makeText(this, "Ingrese sus credenciales de empresa", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Intentar Login Local primero (Soporte Offline Total)
        DBHelper db = new DBHelper(this);
        android.database.Cursor cursor = db.loginFarmacia(cor, cla);
        if (cursor != null && cursor.moveToFirst()) {
            try {
                JSONObject farmDoc = new JSONObject();
                farmDoc.put("_id", cursor.getString(cursor.getColumnIndexOrThrow("id")));
                farmDoc.put("_rev", cursor.getString(cursor.getColumnIndexOrThrow("rev")));
                farmDoc.put("empresa", cursor.getString(cursor.getColumnIndexOrThrow("empresa")));
                farmDoc.put("direccion", cursor.getString(cursor.getColumnIndexOrThrow("direccion")));
                farmDoc.put("telefono", cursor.getString(cursor.getColumnIndexOrThrow("telefono")));
                farmDoc.put("correo", cursor.getString(cursor.getColumnIndexOrThrow("correo")));
                farmDoc.put("clave", cursor.getString(cursor.getColumnIndexOrThrow("clave")));
                farmDoc.put("foto", cursor.getString(cursor.getColumnIndexOrThrow("foto")));
                farmDoc.put("descripcion", cursor.getString(cursor.getColumnIndexOrThrow("descripcion")));
                farmDoc.put("chat_habilitado", cursor.getInt(cursor.getColumnIndexOrThrow("chat_habilitado")) == 1);
                cursor.close();
                Log.d("AdminLogin", "Login local exitoso para: " + cor);
                entrar(farmDoc, false);
                return;
            } catch (Exception e) {
                Log.e("AdminLogin", "Error cargando datos locales", e);
            }
        }
        if (cursor != null) cursor.close();

        // 2. Si no existe local o falló, intentar con Internet
        if (!Utilidades.hayInternet(this)) {
            Toast.makeText(this, "No tienes internet y esta cuenta no está registrada localmente", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Validando en la nube...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                JSONObject selector = new JSONObject();
                JSONObject query = new JSONObject();
                query.put("correo", cor);
                query.put("clave", cla);
                selector.put("selector", query);
                
                // Optimización Extrema: NO pedir la foto en el login para que sea instantáneo
                JSONArray fields = new JSONArray();
                fields.put("_id"); fields.put("_rev"); fields.put("empresa");
                fields.put("direccion"); fields.put("telefono"); fields.put("correo");
                fields.put("descripcion"); fields.put("chat_habilitado");
                // La foto se cargará en segundo plano una vez dentro de la app
                selector.put("fields", fields);
                selector.put("limit", 1);

                TareaServidor tarea = new TareaServidor();
                String respuesta = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                
                JSONObject resJson = new JSONObject(respuesta);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    if (docs.length() > 0) {
                        JSONObject farmDoc = docs.getJSONObject(0);
                        // Asegurar que la clave se guarde para acceso offline
                        farmDoc.put("clave", cla);
                        entrar(farmDoc, true);
                    } else {
                        runOnUiThread(() -> Toast.makeText(AdminLoginActivity.this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                Log.e("AdminLogin", "Error online", e);
                runOnUiThread(() -> Toast.makeText(AdminLoginActivity.this, "Error de red", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void entrar(JSONObject farmDoc, boolean guardarEnLocal) throws Exception {
        String id = farmDoc.getString("_id");
        String nombre = farmDoc.getString("empresa");

        SharedPreferences.Editor editor = getSharedPreferences("AdminPrefs", MODE_PRIVATE).edit();
        editor.putString("farmaciaId", id);
        editor.putString("farmaciaNombre", nombre);
        editor.putString("farmaciaFoto", farmDoc.optString("foto", ""));
        editor.putBoolean("chatHabilitado", farmDoc.optBoolean("chat_habilitado", false));
        editor.apply();

        if (guardarEnLocal) {
            DBHelper db = new DBHelper(this);
            db.administrarFarmacias("nuevo", new String[]{
                    id,
                    farmDoc.optString("_rev", ""),
                    nombre,
                    farmDoc.optString("direccion", ""),
                    farmDoc.optString("telefono", ""),
                    farmDoc.optString("correo", ""),
                    farmDoc.optString("clave", ""),
                    farmDoc.optString("foto", ""),
                    farmDoc.optString("descripcion", ""),
                    farmDoc.optBoolean("chat_habilitado", false) ? "1" : "0"
            });
        }

        runOnUiThread(() -> {
            Toast.makeText(AdminLoginActivity.this, "¡Bienvenido " + nombre + "!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(AdminLoginActivity.this, AdminHomeActivity.class));
            finish();
        });
    }
}
