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

        if (!Utilidades.hayInternet(this)) {
            loginLocal(cor, cla);
            return;
        }

        try {
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
                    entrar(userDoc, true); // true = guardar en local
                } else {
                    // Si no está en la nube, probar local por si acaso se registró offline
                    loginLocal(cor, cla);
                }
            } else {
                loginLocal(cor, cla);
            }

        } catch (Exception e) {
            loginLocal(cor, cla);
        }
    }

    private void loginLocal(String cor, String cla) {
        DBHelper db = new DBHelper(this);
        android.database.Cursor cursor = db.login(cor, cla);
        if (cursor.moveToFirst()) {
            try {
                JSONObject userDoc = new JSONObject();
                userDoc.put("_id", cursor.getString(0));
                userDoc.put("nombres", cursor.getString(1));
                userDoc.put("apellidos", cursor.getString(2));
                userDoc.put("telefono", cursor.getString(3));
                userDoc.put("correo", cursor.getString(4));
                userDoc.put("clave", cursor.getString(5));
                userDoc.put("direccion", cursor.getString(6));
                userDoc.put("alergias", cursor.getString(7));
                userDoc.put("tipo_sangre", cursor.getString(8));
                userDoc.put("enfermedades", cursor.getString(9));
                userDoc.put("foto", cursor.getString(10));
                
                entrar(userDoc, false); // false = no re-guardar
            } catch (Exception e) {
                mostrar("Error local: " + e.getMessage());
            }
        } else {
            mostrar("Usuario no registrado en este equipo o credenciales incorrectas");
        }
        cursor.close();
    }

    private void entrar(JSONObject userDoc, boolean guardarEnLocal) throws Exception {
        String nombre = userDoc.getString("nombres");
        String id = userDoc.getString("_id");
        
        // Guardar datos en Preferences para la sesión actual
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit()
                .putString("userData", userDoc.toString())
                .apply();

        if (guardarEnLocal) {
            DBHelper db = new DBHelper(this);
            String clave = userDoc.optString("clave", txtClave.getText().toString());
            
            // Asegurar persistencia completa de todos los campos
            db.getWritableDatabase().execSQL("INSERT OR REPLACE INTO usuarios(id, nombres, apellidos, telefono, correo, clave, direccion, alergias, tipo_sangre, enfermedades, foto) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    new String[]{
                            id, 
                            nombre, 
                            userDoc.optString("apellidos", ""), 
                            userDoc.optString("telefono", ""), 
                            userDoc.optString("correo", ""), 
                            clave, 
                            userDoc.optString("direccion", ""), 
                            userDoc.optString("alergias", ""), 
                            userDoc.optString("tipo_sangre", ""), 
                            userDoc.optString("enfermedades", ""), 
                            userDoc.optString("foto", "")
                    });
        }

        mostrar("Bienvenido " + nombre);
        
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }




    private void mostrar(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
