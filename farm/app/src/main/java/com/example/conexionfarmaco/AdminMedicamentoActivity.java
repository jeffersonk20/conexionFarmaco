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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class AdminMedicamentoActivity extends AppCompatActivity {

    private EditText etNombre, etPrecio, etStock, etPresentacion;
    private CheckBox cbPromocion;
    private Button btnGuardar, btnCancelar, btnEliminar;
    private ImageView ivFoto1, ivFoto2, ivFoto3;
    private String farmaciaId, farmaciaNombre;
    private JSONObject medEdicion;
    
    private String base64Foto1 = "", base64Foto2 = "", base64Foto3 = "";
    private static final int PICK_IMAGE_1 = 1, PICK_IMAGE_2 = 2, PICK_IMAGE_3 = 3;

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
        
        ivFoto1 = findViewById(R.id.ivAdminMedFoto1);
        ivFoto2 = findViewById(R.id.ivAdminMedFoto2);
        ivFoto3 = findViewById(R.id.ivAdminMedFoto3);

        ivFoto1.setOnClickListener(v -> seleccionarImagen(PICK_IMAGE_1));
        ivFoto2.setOnClickListener(v -> seleccionarImagen(PICK_IMAGE_2));
        ivFoto3.setOnClickListener(v -> seleccionarImagen(PICK_IMAGE_3));

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
                
                base64Foto1 = medEdicion.optString("foto1", "");
                base64Foto2 = medEdicion.optString("foto2", "");
                base64Foto3 = medEdicion.optString("foto3", "");
                
                cargarImagenBase64(base64Foto1, ivFoto1);
                cargarImagenBase64(base64Foto2, ivFoto2);
                cargarImagenBase64(base64Foto3, ivFoto3);
                
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

    private void seleccionarImagen(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                InputStream is = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                
                // Redimensionar para no exceder límites de CouchDB/Memoria
                bitmap = redimensionarBitmap(bitmap, 500);
                
                String base64 = bitmapToBase64(bitmap);
                
                if (requestCode == PICK_IMAGE_1) {
                    base64Foto1 = base64;
                    ivFoto1.setImageBitmap(bitmap);
                    ivFoto1.setColorFilter(null);
                    ivFoto1.setPadding(0,0,0,0);
                } else if (requestCode == PICK_IMAGE_2) {
                    base64Foto2 = base64;
                    ivFoto2.setImageBitmap(bitmap);
                    ivFoto2.setColorFilter(null);
                    ivFoto2.setPadding(0,0,0,0);
                } else if (requestCode == PICK_IMAGE_3) {
                    base64Foto3 = base64;
                    ivFoto3.setImageBitmap(bitmap);
                    ivFoto3.setColorFilter(null);
                    ivFoto3.setPadding(0,0,0,0);
                }
            } catch (Exception e) {
                Log.e("AdminMed", "Error procesando imagen", e);
            }
        }
    }

    private Bitmap redimensionarBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    private void cargarImagenBase64(String base64, ImageView iv) {
        if (base64 != null && !base64.isEmpty()) {
            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            iv.setImageBitmap(decodedByte);
        }
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
                
                // Agregar fotos
                json.put("foto1", base64Foto1);
                json.put("foto2", base64Foto2);
                json.put("foto3", base64Foto3);

                String metodo = medEdicion != null ? "PUT" : "POST";
                String url = (medEdicion != null) ? 
                        Utilidades.url_medicamentos + "/" + json.getString("_id") : 
                        Utilidades.url_medicamentos;

                DBHelper dbHelper = new DBHelper(this);
                // Guardar localmente siempre
                dbHelper.guardarMedicamentoLocal(json);
                
                if (Utilidades.hayInternet(this)) {
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(json.toString(), metodo, url).get();
                    JSONObject resJson = new JSONObject(res);
                    
                    if (resJson.optBoolean("ok", false)) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Guardado correctamente", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                        return;
                    }
                }

                // Si no hay internet o falló el servidor, agregar a pendientes
                dbHelper.agregarPendiente(url, metodo, json.toString(), "couchdb");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Guardado localmente. Se sincronizará al conectar.", Toast.LENGTH_SHORT).show();
                    finish();
                });

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
                        
                        DBHelper dbHelper = new DBHelper(this);
                        dbHelper.eliminarMedicamentoLocal(id);
                        
                        if (Utilidades.hayInternet(this)) {
                            TareaServidor tarea = new TareaServidor();
                            String res = tarea.execute("", "DELETE", url).get();
                            JSONObject resJson = new JSONObject(res);
                            
                            if (resJson.optBoolean("ok", false)) {
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Eliminado con éxito", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                                return;
                            }
                        }

                        // Si no hay internet o falló el servidor, agregar a pendientes
                        dbHelper.agregarPendiente(url, "DELETE", "", "couchdb");
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Eliminado localmente. Se sincronizará al conectar.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                        
                    } catch (Exception e) {
                        Log.e("AdminMed", "Error delete", e);
                        try {
                            String id = medEdicion.getString("_id");
                            DBHelper db = new DBHelper(this);
                            db.eliminarMedicamentoLocal(id);
                            // Intentar agregar a pendientes incluso si falló el bloque try principal
                            String rev = medEdicion.getString("_rev");
                            String url = Utilidades.url_medicamentos + "/" + id + "?rev=" + rev;
                            db.agregarPendiente(url, "DELETE", "", "couchdb");
                        } catch (Exception ex) {}
                        
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Eliminado localmente. Sincronización pendiente.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }).start();
            })
            .setNegativeButton("No", null)
            .show();
    }
}
