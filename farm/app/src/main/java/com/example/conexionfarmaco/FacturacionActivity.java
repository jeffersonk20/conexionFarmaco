package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class FacturacionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_facturacion);

        android.widget.EditText etNombre = findViewById(R.id.etFactNombre);
        android.widget.EditText etDireccion = findViewById(R.id.etFactDireccion);
        android.widget.EditText etTel = findViewById(R.id.etFactTel);

        findViewById(R.id.btnFactAtras).setOnClickListener(v -> finish());

        String metodoPago = getIntent().getStringExtra("metodo_pago");

        Button btnContinuar = findViewById(R.id.btnFactContinuar);
        btnContinuar.setOnClickListener(v -> {
            String nom = etNombre.getText().toString();
            String dir = etDireccion.getText().toString();
            String tel = etTel.getText().toString();

            if (nom.isEmpty() || dir.isEmpty() || tel.isEmpty()) {
                Toast.makeText(this, "Complete todos los campos de envío", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, ResumenPedidoActivity.class);
            intent.putExtra("metodo_pago", metodoPago);
            intent.putExtra("fact_nombre", nom);
            intent.putExtra("fact_direccion", dir);
            intent.putExtra("fact_tel", tel);
            startActivity(intent);
        });
    }
}
