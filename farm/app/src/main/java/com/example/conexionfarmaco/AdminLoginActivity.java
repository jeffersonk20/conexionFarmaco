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
    private boolean procesando = false;

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
        if (procesando) return;

        String cor = etCorreo.getText().toString().trim();
        String cla = etPass.getText().toString().trim();

        if (cor.isEmpty() || cla.isEmpty()) {
            Toast.makeText(this, "Ingrese sus credenciales de empresa", Toast.LENGTH_SHORT).show();
            return;
        }

        procesando = true;
        btnIngresar.setEnabled(false);
        Toast.makeText(this, "Iniciando sesión...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // 1. INTENTO LOCAL (Rápido y sin red)
                DBHelper db = new DBHelper(this);
                android.database.Cursor cursor = db.loginFarmacia(cor, cla);
                
                if (cursor != null && cursor.moveToFirst()) {
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
                    
                    Log.d("AdminLogin", "Login local exitoso");
                    entrar(farmDoc, false);
                    return;
                }
                if (cursor != null) cursor.close();

                // 2. INTENTO EN LA NUBE (Solo si el local falla)
                if (!Utilidades.hayInternet(this)) {
                    finalizarConError("No hay internet y no se encontró registro local");
                    return;
                }

                JSONObject selector = new JSONObject();
                JSONObject query = new JSONObject();
                query.put("correo", cor);
                query.put("clave", cla);
                selector.put("selector", query);
                
                // Optimización de campos: NO descargar la foto aquí (es muy pesada)
                JSONArray fields = new JSONArray();
                fields.put("_id"); fields.put("_rev"); fields.put("empresa");
                fields.put("direccion"); fields.put("telefono"); fields.put("correo");
                fields.put("descripcion"); fields.put("chat_habilitado");
                selector.put("fields", fields);
                selector.put("limit", 1);

                TareaServidor tarea = new TareaServidor();
                String respuesta = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                
                JSONObject resJson = new JSONObject(respuesta);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    if (docs.length() > 0) {
                        JSONObject farmDoc = docs.getJSONObject(0);
                        farmDoc.put("clave", cla); // Guardar clave para futuros accesos offline
                        entrar(farmDoc, true);
                    } else {
                        finalizarConError("Credenciales incorrectas");
                    }
                } else {
                    finalizarConError("Error de comunicación con el servidor");
                }

            } catch (Exception e) {
                Log.e("AdminLogin", "Error login", e);
                finalizarConError("Error crítico: " + e.getMessage());
            }
        }).start();
    }

    private void finalizarConError(String msg) {
        runOnUiThread(() -> {
            procesando = false;
            btnIngresar.setEnabled(true);
            Toast.makeText(AdminLoginActivity.this, msg, Toast.LENGTH_LONG).show();
        });
    }

    private void entrar(JSONObject farmDoc, boolean guardarEnLocal) throws Exception {
        String id = farmDoc.getString("_id");
        String nombre = farmDoc.getString("empresa");

        // 1. Guardar en SharedPreferences de inmediato
        SharedPreferences.Editor editor = getSharedPreferences("AdminPrefs", MODE_PRIVATE).edit();
        editor.putString("farmaciaId", id);
        editor.putString("farmaciaNombre", nombre);
        // Si no hay foto (porque es login online), la dejamos vacía; se cargará en el Home.
        editor.putString("farmaciaFoto", farmDoc.optString("foto", ""));
        editor.putBoolean("chatHabilitado", farmDoc.optBoolean("chat_habilitado", false));
        editor.apply();

        // 2. Guardar en DB local en segundo plano para no retrasar el inicio
        if (guardarEnLocal) {
            new Thread(() -> {
                try {
                    DBHelper db = new DBHelper(AdminLoginActivity.this);
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
                } catch (Exception e) {
                    Log.e("AdminLogin", "Error persistiendo datos", e);
                }
            }).start();
        }

        // 3. Pasar al Home de inmediato
        runOnUiThread(() -> {
            Toast.makeText(AdminLoginActivity.this, "¡Bienvenido " + nombre + "!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(AdminLoginActivity.this, AdminHomeActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
