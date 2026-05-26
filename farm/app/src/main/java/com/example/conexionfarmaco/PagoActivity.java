package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.util.Log;

public class PagoActivity extends AppCompatActivity {

    private EditText etNumero, etVenc, etCVV, etNombre;
    private WebView webView;
    private View scrollForm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pago_tarjeta);

        etNumero = findViewById(R.id.etNumeroTarjeta);
        etVenc = findViewById(R.id.etFechaVenc);
        etCVV = findViewById(R.id.etCVV);
        etNombre = findViewById(R.id.etNombreTarjeta);
        webView = findViewById(R.id.wvWompi);
        scrollForm = findViewById(R.id.scrollFormularioPago);

        findViewById(R.id.btnPagoAtras).setOnClickListener(v -> finish());

        // Verificamos si venimos de ResumenPedido con una URL de Wompi
        String urlPago = getIntent().getStringExtra("urlPago");
        if (urlPago != null && !urlPago.isEmpty()) {
            mostrarWebView(urlPago);
        }

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

    private void mostrarWebView(String url) {
        scrollForm.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d("Wompi", "Cargando URL: " + url);

                // Detectamos cuando Wompi intenta redirigir al dominio "pago-finalizado.com"
                if (url.contains("pago-finalizado.com")) {
                    // El pago terminó. Regresamos a ResumenPedidoActivity con el resultado
                    Intent intent = new Intent(PagoActivity.this, ResumenPedidoActivity.class);
                    intent.setData(android.net.Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                    return true;
                }
                return false;
            }
        });
        
        webView.loadUrl(url);
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