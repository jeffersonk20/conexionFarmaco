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
        String cor = etCorreo.getText().toString();
        String cla = etPass.getText().toString();

        if (cor.isEmpty() || cla.isEmpty()) {
            Toast.makeText(this, "Ingrese sus credenciales de empresa", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Utilidades.hayInternet(this)) {
            loginLocal(cor, cla);
            return;
        }

        new Thread(() -> {
            try {
                JSONObject selector = new JSONObject();
                JSONObject query = new JSONObject();
                query.put("correo", cor);
                query.put("clave", cla);
                selector.put("selector", query);
                selector.put("limit", 1);

                TareaServidor tarea = new TareaServidor();
                // Buscar en la base de datos de farmacias
                String respuesta = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                
                JSONObject resJson = new JSONObject(respuesta);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    if (docs.length() > 0) {
                        JSONObject farmDoc = docs.getJSONObject(0);
                        // Asegurar que la clave esté en el objeto para guardarla localmente
                        if (!farmDoc.has("clave")) farmDoc.put("clave", cla);
                        entrar(farmDoc, true); // true = guardar en local
                    } else {
                        loginLocal(cor, cla);
                    }
                } else {
                    loginLocal(cor, cla);
                }

            } catch (Exception e) {
                loginLocal(cor, cla);
            }
        }).start();
    }

    private void loginLocal(String cor, String cla) {
        DBHelper db = new DBHelper(this);
        android.database.Cursor cursor = db.loginFarmacia(cor, cla);
        if (cursor.moveToFirst()) {
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
                entrar(farmDoc, false); // false = no re-guardar en DB
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error local: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        } else {
            runOnUiThread(() -> Toast.makeText(this, "Credenciales incorrectas o sin registro local", Toast.LENGTH_SHORT).show());
        }
        cursor.close();
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
            String clave = farmDoc.optString("clave", etPass.getText().toString());
            
            // Usar INSERT OR REPLACE para asegurar que tenemos el perfil completo
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
            Toast.makeText(this, "Bienvenido " + nombre, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, AdminHomeActivity.class));
            finish();
        });
    }



}
