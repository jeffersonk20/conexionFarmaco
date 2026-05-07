package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnIrLogin = findViewById(R.id.btnIrLogin);
        Button btnIrRegistro = findViewById(R.id.btnIrRegistro);

        btnIrLogin.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        });

        btnIrRegistro.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, RegistroActivity.class));
        });

        findViewById(R.id.tvIrAdmin).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AdminMainActivity.class));
        });
    }
}
