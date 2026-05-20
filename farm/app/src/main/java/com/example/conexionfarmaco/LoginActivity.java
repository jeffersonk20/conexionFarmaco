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
        switch (biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            case androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS:
                break;
            case androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                mostrar("Este dispositivo no tiene sensor biométrico");
                return;
            case androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                mostrar("El sensor biométrico no está disponible");
                return;
            case androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                mostrar("No tienes huellas registradas en tu equipo");
                return;
            default:
                mostrar("Error desconocido en biometría");
                return;
        }

        String lastUser = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("lastUserData", "");
        if (lastUser.isEmpty()) {
            mostrar("Debe ingresar con contraseña la primera vez para activar la huella");
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(LoginActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // Si el error es porque el usuario canceló, no mostrar nada molesto
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    mostrar("Error de autenticación: " + errString);
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                try {
                    JSONObject userDoc = new JSONObject(lastUser);
                    entrar(userDoc, false);
                } catch (Exception e) {
                    mostrar("Error al recuperar sesión");
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                mostrar("Huella no reconocida");
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Ingreso Rápido")
                .setSubtitle("Use su huella digital para entrar")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
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
                .putString("lastUserData", userDoc.toString()) // Nueva clave persistente para biometría
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
