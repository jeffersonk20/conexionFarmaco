package com.example.conexionfarmaco;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class AdminPerfilActivity extends AppCompatActivity {

    private ImageView imgLogo;
    private TextView tvNombreHeader, tvCorreo, tvStatMed, tvStatVentas;
    private EditText etNombre, etDireccion, etTelefono, etDesc;
    private Button btnCerrarSesion, btnActualizar;
    private String farmaciaId, fotoBase64 = "";
    private JSONObject farmaciaActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_perfil);

        imgLogo = findViewById(R.id.imgAdminPerfilLogo);
        tvNombreHeader = findViewById(R.id.tvAdminPerfilNombre);
        tvCorreo = findViewById(R.id.tvAdminPerfilCorreo);
        tvStatMed = findViewById(R.id.tvAdminStatTotalMed);
        tvStatVentas = findViewById(R.id.tvAdminStatVentas);

        etNombre = findViewById(R.id.etAdminPerfilNombre);
        etDireccion = findViewById(R.id.etAdminPerfilDireccion);
        etTelefono = findViewById(R.id.etAdminPerfilTelefono);
        etDesc = findViewById(R.id.etAdminPerfilDesc);

        btnCerrarSesion = findViewById(R.id.btnAdminPerfilCerrarSesion);
        btnActualizar = findViewById(R.id.btnActualizarPerfil);

        findViewById(R.id.toolbarAdminPerfil).setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences("AdminPrefs", MODE_PRIVATE);
        farmaciaId = prefs.getString("farmaciaId", "");

        cargarDatosFarmacia();
        cargarEstadisticas();

        btnCerrarSesion.setOnClickListener(v -> cerrarSesion());
        btnActualizar.setOnClickListener(v -> guardarCambios());
        imgLogo.setOnClickListener(v -> seleccionarImagen());
    }

    private void cargarEstadisticas() {
        new Thread(() -> {
            DBHelper db = new DBHelper(this);
            // Medicamentos
            List<JSONObject> meds = db.obtenerMedicamentosCache(null, false);
            int countMeds = 0;
            for (JSONObject m : meds) if (m.optString("id_farmacia").equals(farmaciaId)) countMeds++;
            
            // Ventas (pedidos terminados o totales)
            List<JSONObject> pedidos = db.obtenerPedidosAdminCache();
            int countVentas = 0;
            for (JSONObject p : pedidos) {
                JSONArray ids = p.optJSONArray("farmacias_ids");
                if (ids != null) {
                    for (int i = 0; i < ids.length(); i++) {
                        if (ids.optString(i).equals(farmaciaId)) {
                            countVentas++;
                            break;
                        }
                    }
                }
            }

            final int fMeds = countMeds;
            final int fVentas = countVentas;
            runOnUiThread(() -> {
                tvStatMed.setText(String.valueOf(fMeds));
                tvStatVentas.setText(String.valueOf(fVentas));
            });
        }).start();
    }

    private void seleccionarImagen() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 200);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                Bitmap bitmap = Utilidades.obtenerBitmapRotado(this, uri);
                
                if (bitmap != null) {
                    // Redimensionar para optimizar
                    bitmap = redimensionarBitmap(bitmap, 500);
                    imgLogo.setImageBitmap(bitmap);
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                    byte[] bytes = baos.toByteArray();
                    fotoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error al procesar imagen", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap redimensionarBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        float ratio = (float) width / (float) height;
        if (ratio > 1) {
            width = maxSize;
            height = (int) (width / ratio);
        } else {
            height = maxSize;
            width = (int) (height * ratio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    private void guardarCambios() {
        try {
            String nom = etNombre.getText().toString();
            String dir = etDireccion.getText().toString();
            String tel = etTelefono.getText().toString();
            String des = etDesc.getText().toString();

            if (nom.isEmpty()) {
                Toast.makeText(this, "El nombre es obligatorio", Toast.LENGTH_SHORT).show();
                return;
            }

            DBHelper db = new DBHelper(this);
            if (farmaciaActual == null) {
                farmaciaActual = db.obtenerFarmaciaLocal(farmaciaId);
                if (farmaciaActual == null) {
                    farmaciaActual = new JSONObject();
                    farmaciaActual.put("_id", farmaciaId);
                }
            }
            
            farmaciaActual.put("empresa", nom);
            farmaciaActual.put("direccion", dir);
            farmaciaActual.put("telefono", tel);
            farmaciaActual.put("descripcion", des);
            
            // Si el usuario eligió una foto nueva, usarla; si no, mantener la que ya tiene farmaciaActual
            if (!fotoBase64.isEmpty()) {
                farmaciaActual.put("foto", fotoBase64);
            }

            // Usamos guardarFarmaciaCache con forzar=true porque el usuario está editando activamente
            db.guardarFarmaciaCache(farmaciaActual, true);

            // Sincronizar con la nube
            String urlUpdate = Utilidades.url_farmacias + "/" + farmaciaId;
            db.agregarPendiente(urlUpdate, "PUT", farmaciaActual.toString(), "couchdb");
            Utilidades.sincronizar(this);

            Toast.makeText(this, "Perfil actualizado localmente. Sincronizando...", Toast.LENGTH_SHORT).show();
            tvNombreHeader.setText(nom);

            // ACTUALIZAR SharedPreferences para que el chat use los nuevos datos
            SharedPreferences.Editor editor = getSharedPreferences("AdminPrefs", MODE_PRIVATE).edit();
            editor.putString("farmaciaNombre", nom);
            editor.putString("farmaciaFoto", farmaciaActual.optString("foto", ""));
            editor.apply();

        } catch (Exception e) {
            Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void cargarDatosFarmacia() {
        if (farmaciaId.isEmpty()) return;

        DBHelper db = new DBHelper(this);
        farmaciaActual = db.obtenerFarmaciaLocal(farmaciaId);
        if (farmaciaActual != null) {
            actualizarUI(farmaciaActual);
        }

        if (Utilidades.hayInternet(this)) {
            new Thread(() -> {
                try {
                    // Fetch by ID directly instead of using _find to avoid index delay
                    String url = Utilidades.url_farmacias + "/" + farmaciaId;
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute("", "GET", url).get();
                    
                    if (res != null && !res.contains("Error")) {
                        JSONObject docServidor = new JSONObject(res);
                        
                        // Solo actualizar localmente si no hay cambios pendientes de subir
                        if (!db.estaPendienteSincronizacion(farmaciaId)) {
                            // IMPORTANTE: El servidor puede no devolver correo/clave, preservamos los locales
                            JSONObject local = db.obtenerFarmaciaLocal(farmaciaId);
                            if (local != null) {
                                if (!docServidor.has("correo")) docServidor.put("correo", local.optString("correo"));
                                if (!docServidor.has("clave")) docServidor.put("clave", local.optString("clave"));
                            }
                            farmaciaActual = docServidor;
                            db.guardarFarmaciaCache(farmaciaActual, false);
                            runOnUiThread(() -> actualizarUI(farmaciaActual));
                        }
                    }
                } catch (Exception e) {
                    Log.e("AdminPerfil", "Error red", e);
                }
            }).start();
        }
    }

    private void actualizarUI(JSONObject farm) {
        try {
            tvNombreHeader.setText(farm.optString("empresa", "Empresa"));
            etNombre.setText(farm.optString("empresa", ""));
            etDireccion.setText(farm.optString("direccion", ""));
            etTelefono.setText(farm.optString("telefono", ""));
            tvCorreo.setText(farm.optString("correo", "Sin correo"));
            etDesc.setText(farm.optString("descripcion", ""));

            String foto = farm.optString("foto", "");
            if (!foto.isEmpty()) {
                Utilidades.cargarImagenBase64(foto, imgLogo);
            }
        } catch (Exception e) {
            Log.e("AdminPerfil", "Error UI", e);
        }
    }

    private void cerrarSesion() {
        getSharedPreferences("AdminPrefs", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
