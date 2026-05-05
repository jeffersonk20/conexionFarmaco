package com.example.conexionfarmaco;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    EditText txtCorreo, txtClave;
    Button btnIngresar;
    DBHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = new DBHelper(this);

        txtCorreo = findViewById(R.id.etCorreo);
        txtClave = findViewById(R.id.etPassword);
        btnIngresar = findViewById(R.id.btnIngresar);

        btnIngresar.setOnClickListener(v -> {
            String cor = txtCorreo.getText().toString();
            String cla = txtClave.getText().toString();

            if (cor.isEmpty() || cla.isEmpty()) {
                mostrar("Ingrese credenciales");
            } else {
                Cursor c = db.login(cor, cla);
                if (c.moveToFirst()) {
                    mostrar("Bienvenido " + c.getString(1));
                    // Aquí podrías abrir la pantalla principal (Home)
                } else {
                    mostrar("Correo o clave incorrectos");
                }
            }
        });
    }

    private void mostrar(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
