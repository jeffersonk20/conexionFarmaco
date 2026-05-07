package com.example.conexionfarmaco;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class AdminMedicamentoActivity extends AppCompatActivity {

    private EditText etNombre, etPrecio, etStock, etPresentacion;
    private CheckBox cbPromocion;
    private Button btnGuardar, btnCancelar, btnEliminar;
    private String farmaciaId, farmaciaNombre;
    private JSONObject medEdicion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_medicamento);

        etNombre = findViewById(R.id.etAdminMedNombre);
        etPrecio = findViewById(R.id.etAdminMedPrecio);
        etStock = findViewById(R.id.etAdminMedStock);
        etPresentacion = findViewById(R.id.etAdminMedPresentacion);
        cbPromocion = findViewById(R.id.cbAdminMedPromocion);
        btnGuardar = findViewById(R.id.btnAdminMedGuardar);
        btnCancelar = findViewById(R.id.btnAdminMedCancelar);
        btnEliminar = findViewById(R.id.btnAdminMedEliminar);

        SharedPreferences prefs = getSharedPreferences("AdminPrefs", MODE_PRIVATE);
        farmaciaId = prefs.getString("farmaciaId", "");
        farmaciaNombre = prefs.getString("farmaciaNombre", "");

        String data = getIntent().getStringExtra("medData");
        if (data != null) {
            try {
                medEdicion = new JSONObject(data);
                etNombre.setText(medEdicion.getString("nombre"));
                etPrecio.setText(medEdicion.getString("precio"));
                etStock.setText(medEdicion.optString("stock", ""));
                etPresentacion.setText(medEdicion.optString("presentacion", ""));
                cbPromocion.setChecked(medEdicion.optBoolean("promocion", false));
                
                ((TextView)findViewById(R.id.tvAdminTituloMed)).setText("Editar Medicamento");
                btnGuardar.setText("Actualizar");
                btnEliminar.setVisibility(View.VISIBLE);
                
                btnEliminar.setOnClickListener(v -> eliminarMedicamento());
            } catch (Exception e) {
                Log.e("AdminMed", "Error parsing", e);
            }
        }

        btnGuardar.setOnClickListener(v -> guardarMedicamento());
        btnCancelar.setOnClickListener(v -> finish());
    }

    private void guardarMedicamento() {
        String nom = etNombre.getText().toString();
        String pre = etPrecio.getText().toString();
        String sto = etStock.getText().toString();
        String preS = etPresentacion.getText().toString();
        boolean pro = cbPromocion.isChecked();

        if (nom.isEmpty() || pre.isEmpty()) {
            Toast.makeText(this, "Nombre y precio son requeridos", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json = medEdicion != null ? medEdicion : new JSONObject();
                if (medEdicion == null) {
                    json.put("_id", Utilidades.generarId());
                    json.put("id_farmacia", farmaciaId);
                    json.put("nombre_farmacia", farmaciaNombre);
                }
                
                json.put("nombre", nom);
                json.put("precio", pre);
                json.put("stock", sto);
                json.put("presentacion", preS);
                json.put("promocion", pro);
                json.put("tipo", "medicamento");

                TareaServidor tarea = new TareaServidor();
                String metodo = medEdicion != null ? "PUT" : "POST";
                String url = medEdicion != null ? Utilidades.url_medicamentos + "/" + json.getString("_id") : Utilidades.url_medicamentos;
                
                String res = tarea.execute(json.toString(), metodo, url).get();
                JSONObject resJson = new JSONObject(res);
                
                if (resJson.optBoolean("ok", false)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Guardado correctamente", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            } catch (Exception e) {
                Log.e("AdminMed", "Error guardado", e);
            }
        }).start();
    }

    private void eliminarMedicamento() {
        if (medEdicion == null) return;
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Estás seguro de eliminar este medicamento?")
            .setPositiveButton("Sí, eliminar", (dialog, which) -> {
                new Thread(() -> {
                    try {
                        String id = medEdicion.getString("_id");
                        String rev = medEdicion.getString("_rev");
                        String url = Utilidades.url_medicamentos + "/" + id + "?rev=" + rev;
                        
                        TareaServidor tarea = new TareaServidor();
                        String res = tarea.execute("", "DELETE", url).get();
                        
                        if (new JSONObject(res).optBoolean("ok", false)) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Eliminado con éxito", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    } catch (Exception e) {
                        Log.e("AdminMed", "Error delete", e);
                    }
                }).start();
            })
            .setNegativeButton("No", null)
            .show();
    }
}
