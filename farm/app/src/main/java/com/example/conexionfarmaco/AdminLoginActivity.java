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

        new Thread(() -> {
            try {
                JSONObject selector = new JSONObject();
                JSONObject query = new JSONObject();
                query.put("correo", cor);
                query.put("clave", cla);
                query.put("tipo", "farmacia");
                selector.put("selector", query);
                selector.put("limit", 1);

                TareaServidor tarea = new TareaServidor();
                String respuesta = tarea.execute(selector.toString(), "POST", Utilidades.url_find_farmacias).get();
                
                JSONObject resJson = new JSONObject(respuesta);
                if (resJson.has("docs")) {
                    JSONArray docs = resJson.getJSONArray("docs");
                    if (docs.length() > 0) {
                        JSONObject farmDoc = docs.getJSONObject(0);
                        String id = farmDoc.getString("_id");
                        String nombre = farmDoc.getString("empresa");

                        // Guardar sesión admin
                        SharedPreferences.Editor editor = getSharedPreferences("AdminPrefs", MODE_PRIVATE).edit();
                        editor.putString("farmaciaId", id);
                        editor.putString("farmaciaNombre", nombre);
                        editor.apply();

                        runOnUiThread(() -> {
                            Toast.makeText(this, "Bienvenido " + nombre, Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, AdminHomeActivity.class));
                            finish();
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Error del servidor", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                Log.e("AdminLogin", "Error", e);
                runOnUiThread(() -> Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
