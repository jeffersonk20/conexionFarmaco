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
                farmDoc.put("_id", cursor.getString(0));
                farmDoc.put("empresa", cursor.getString(1));
                farmDoc.put("direccion", cursor.getString(2));
                farmDoc.put("telefono", cursor.getString(3));
                farmDoc.put("correo", cursor.getString(4));
                farmDoc.put("clave", cursor.getString(5));
                farmDoc.put("foto", cursor.getString(6));
                farmDoc.put("descripcion", cursor.getString(7));
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
                // CouchDB selector (Suele ser sensible a mayúsculas, enviamos tal cual)
                JSONObject query = new JSONObject();
                query.put("correo", cor);
                query.put("clave", cla);
                
                JSONObject selector = new JSONObject();
                selector.put("selector", query);
                selector.put("limit", 1);

                TareaServidor tarea = new TareaServidor();
                String respuesta = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                
                if (respuesta == null || respuesta.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(AdminLoginActivity.this, "Servidor no responde", Toast.LENGTH_SHORT).show());
                    return;
                }

                JSONObject resJson = new JSONObject(respuesta);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    if (docs.length() > 0) {
                        JSONObject farmDoc = docs.getJSONObject(0);
                        // Muy importante: Guardar la clave que funcionó para el acceso offline
                        farmDoc.put("clave", cla);
                        entrar(farmDoc, true);
                    } else {
                        runOnUiThread(() -> Toast.makeText(AdminLoginActivity.this, "Correo o contraseña de empresa incorrectos", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(AdminLoginActivity.this, "Error en la respuesta del servidor", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                Log.e("AdminLogin", "Error en login por internet", e);
                runOnUiThread(() -> Toast.makeText(AdminLoginActivity.this, "Error de red: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void entrar(JSONObject farmDoc, boolean guardarEnLocal) throws Exception {
        String id = farmDoc.getString("_id");
        String nombre = farmDoc.getString("empresa");

        // Guardar sesión admin en Preferences
        SharedPreferences.Editor editor = getSharedPreferences("AdminPrefs", MODE_PRIVATE).edit();
        editor.putString("farmaciaId", id);
        editor.putString("farmaciaNombre", nombre);
        editor.apply();

        if (guardarEnLocal) {
            DBHelper db = new DBHelper(this);
            String clave = farmDoc.optString("clave", "");
            
            db.getWritableDatabase().execSQL("INSERT OR REPLACE INTO farmacias(id, empresa, direccion, telefono, correo, clave, foto, descripcion) VALUES(?,?,?,?,?,?,?,?)",
                    new String[]{
                            id, 
                            nombre, 
                            farmDoc.optString("direccion", ""), 
                            farmDoc.optString("telefono", ""), 
                            farmDoc.optString("correo", ""), 
                            clave, 
                            farmDoc.optString("foto", ""), 
                            farmDoc.optString("descripcion", "")
                    });
        }

        runOnUiThread(() -> {
            Toast.makeText(AdminLoginActivity.this, "¡Bienvenido " + nombre + "!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(AdminLoginActivity.this, AdminHomeActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
