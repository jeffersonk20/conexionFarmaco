package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    EditText txtCorreo, txtClave;
    Button btnIngresar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        txtCorreo = findViewById(R.id.etCorreo);
        txtClave = findViewById(R.id.etPassword);
        btnIngresar = findViewById(R.id.btnIngresar);

        btnIngresar.setOnClickListener(v -> loginNube());
    }

    private void loginNube() {
        String cor = txtCorreo.getText().toString();
        String cla = txtClave.getText().toString();

        if (cor.isEmpty() || cla.isEmpty()) {
            mostrar("Ingrese sus credenciales");
            return;
        }

        try {
            // Construir el selector para CouchDB
            JSONObject selector = new JSONObject();
            JSONObject query = new JSONObject();
            query.put("correo", cor);
            query.put("clave", cla);
            selector.put("selector", query);
            selector.put("limit", 1);

            TareaServidor tarea = new TareaServidor();
            String respuesta = tarea.execute(selector.toString(), "POST", Utilidades.url_find).get();
            
            JSONObject resJson = new JSONObject(respuesta);
            if (resJson.has("docs")) {
                JSONArray docs = resJson.getJSONArray("docs");
                if (docs.length() > 0) {
                    JSONObject userDoc = docs.getJSONObject(0);
                    String nombre = userDoc.getString("nombres");
                    
                    mostrar("Bienvenido " + nombre);
                    
                    Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    mostrar("Correo o clave incorrectos");
                }
            } else {
                mostrar("Error en el servidor: " + respuesta);
            }

        } catch (Exception e) {
            mostrar("Error de conexión: " + e.getMessage());
        }
    }

    private void mostrar(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
