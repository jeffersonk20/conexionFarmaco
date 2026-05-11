package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PagoActivity extends AppCompatActivity {

    private EditText etNumero, etVenc, etCVV, etNombre;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pago_tarjeta);

        etNumero = findViewById(R.id.etNumeroTarjeta);
        etVenc = findViewById(R.id.etFechaVenc);
        etCVV = findViewById(R.id.etCVV);
        etNombre = findViewById(R.id.etNombreTarjeta);

        findViewById(R.id.btnPagoAtras).setOnClickListener(v -> finish());

        findViewById(R.id.btnEscanearTarjeta).setOnClickListener(v -> simularEscaneo());

        Button btnContinuar = findViewById(R.id.btnAgregarTarjetaContinuar);
        btnContinuar.setOnClickListener(v -> {
            if (etNumero.getText().toString().isEmpty()) {
                Toast.makeText(this, "Ingrese los datos de la tarjeta", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, FacturacionActivity.class);
            intent.putExtra("metodo_pago", "tarjeta");
            startActivity(intent);
        });
    }

    private void simularEscaneo() {
        Toast.makeText(this, "Iniciando escáner inteligente...", Toast.LENGTH_SHORT).show();
        
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, 101);
        } catch (Exception e) {
            // Fallback si la cámara no responde
            Toast.makeText(this, "Procesando tarjeta...", Toast.LENGTH_SHORT).show();
            new android.os.Handler().postDelayed(this::llenarDatosMock, 2000);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101) {
            llenarDatosMock();
            Toast.makeText(this, "Tarjeta escaneada con éxito", Toast.LENGTH_SHORT).show();
        }
    }

    private void llenarDatosMock() {
        etNumero.setText("4550 1234 5678 9012");
        etVenc.setText("12/26");
        etCVV.setText("888");
        etNombre.setText("USUARIO PRUEBA");
    }
}