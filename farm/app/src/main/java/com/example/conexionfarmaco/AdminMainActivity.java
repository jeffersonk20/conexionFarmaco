package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AdminMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_main);

        Button btnIrLogin = findViewById(R.id.btnAdminIrLogin);
        Button btnIrRegistro = findViewById(R.id.btnAdminIrRegistro);

        btnIrLogin.setOnClickListener(v -> {
            startActivity(new Intent(AdminMainActivity.this, AdminLoginActivity.class));
        });

        btnIrRegistro.setOnClickListener(v -> {
            startActivity(new Intent(AdminMainActivity.this, AdminRegistroActivity.class));
        });
    }
}
