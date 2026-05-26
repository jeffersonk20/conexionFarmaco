package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.Executor;

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

        findViewById(R.id.ivBiometric).setOnClickListener(v -> loginBiometrico());
        findViewById(R.id.tvRegistro).setOnClickListener(v -> {
            startActivity(new Intent(this, RegistroActivity.class));
        });
    }

    private void loginBiometrico() {
        androidx.biometric.BiometricManager biometricManager = androidx.biometric.BiometricManager.from(this);
        if (biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            mostrar("Biometría no disponible o no configurada");
            return;
        }

        String lastUser = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("lastUserData", "");
        if (lastUser.isEmpty()) {
            mostrar("Ingrese con contraseña primero para activar huella");
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(LoginActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                try {
                    entrar(new JSONObject(lastUser), false);
                } catch (Exception e) { mostrar("Error al entrar"); }
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Ingreso Rápido")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void loginNube() {
        String cor = txtCorreo.getText().toString().trim();
        String cla = txtClave.getText().toString().trim();

        if (cor.isEmpty() || cla.isEmpty()) {
            mostrar("Ingrese sus credenciales");
            return;
        }

        if (!Utilidades.hayInternet(this)) {
            loginLocal(cor, cla);
            return;
        }

        // Mostrar feedback de carga
        btnIngresar.setEnabled(false);
        btnIngresar.setText("Validando...");

        new Thread(() -> {
            try {
                JSONObject selector = new JSONObject();
                selector.put("selector", new JSONObject().put("correo", cor).put("clave", cla));
                // Solo pedir lo que necesitamos para el objeto de usuario
                JSONArray fields = new JSONArray();
                fields.put("_id"); fields.put("_rev"); fields.put("nombres");
                fields.put("apellidos"); fields.put("telefono"); fields.put("correo");
                fields.put("direccion"); fields.put("alergias"); fields.put("tipo_sangre");
                fields.put("enfermedades"); fields.put("foto");
                selector.put("fields", fields);
                selector.put("limit", 1);
                
                TareaServidor tarea = new TareaServidor();
                String respuesta = tarea.execute(selector.toString(), "POST", Utilidades.url_find).get();
                JSONObject resJson = new JSONObject(respuesta);

                runOnUiThread(() -> {
                    btnIngresar.setEnabled(true);
                    btnIngresar.setText("Ingresar");
                });

                if (resJson.has("docs") && resJson.getJSONArray("docs").length() > 0) {
                    JSONObject userDoc = resJson.getJSONArray("docs").getJSONObject(0);
                    userDoc.put("clave", cla); // Guardar clave para uso offline
                    entrar(userDoc, true);
                } else {
                    runOnUiThread(() -> loginLocal(cor, cla));
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnIngresar.setEnabled(true);
                    btnIngresar.setText("Ingresar");
                    loginLocal(cor, cla);
                });
            }
        }).start();
    }

    private void loginLocal(String cor, String cla) {
        DBHelper db = new DBHelper(this);
        android.database.Cursor cursor = db.login(cor, cla);
        if (cursor != null && cursor.moveToFirst()) {
            try {
                JSONObject userDoc = new JSONObject();
                userDoc.put("_id", cursor.getString(cursor.getColumnIndexOrThrow("id")));
                userDoc.put("_rev", cursor.getString(cursor.getColumnIndexOrThrow("rev")));
                userDoc.put("nombres", cursor.getString(cursor.getColumnIndexOrThrow("nombres")));
                userDoc.put("apellidos", cursor.getString(cursor.getColumnIndexOrThrow("apellidos")));
                userDoc.put("telefono", cursor.getString(cursor.getColumnIndexOrThrow("telefono")));
                userDoc.put("correo", cursor.getString(cursor.getColumnIndexOrThrow("correo")));
                userDoc.put("clave", cursor.getString(cursor.getColumnIndexOrThrow("clave")));
                userDoc.put("direccion", cursor.getString(cursor.getColumnIndexOrThrow("direccion")));
                userDoc.put("alergias", cursor.getString(cursor.getColumnIndexOrThrow("alergias")));
                userDoc.put("tipo_sangre", cursor.getString(cursor.getColumnIndexOrThrow("tipo_sangre")));
                userDoc.put("enfermedades", cursor.getString(cursor.getColumnIndexOrThrow("enfermedades")));
                userDoc.put("foto", cursor.getString(cursor.getColumnIndexOrThrow("foto")));
                cursor.close();
                entrar(userDoc, false);
            } catch (Exception e) {
                runOnUiThread(() -> mostrar("Error local: " + e.getMessage()));
            }
        } else {
            runOnUiThread(() -> mostrar("Usuario no encontrado localmente"));
        }
    }

    private void entrar(JSONObject userDoc, boolean guardarEnLocal) throws Exception {
        String id = userDoc.getString("_id");
        String nombre = userDoc.getString("nombres");
        
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit()
                .putString("userData", userDoc.toString())
                .putString("lastUserData", userDoc.toString())
                .apply();

        if (guardarEnLocal) {
            DBHelper db = new DBHelper(this);
            db.administrarUsuarios("nuevo", new String[]{
                    id,
                    userDoc.optString("_rev", ""),
                    nombre,
                    userDoc.optString("apellidos", ""),
                    userDoc.optString("telefono", ""),
                    userDoc.optString("correo", ""),
                    userDoc.optString("clave", ""),
                    userDoc.optString("direccion", ""),
                    userDoc.optString("alergias", ""),
                    userDoc.optString("tipo_sangre", ""),
                    userDoc.optString("enfermedades", ""),
                    userDoc.optString("foto", "")
            });
        }

        runOnUiThread(() -> {
            Toast.makeText(this, "¡Bienvenido " + nombre + "!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }

    private void mostrar(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
