package com.example.conexionfarmaco;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdminRegistroActivity extends AppCompatActivity {

    private EditText etEmpresa, etDireccion, etTelefono, etCorreo, etDescripcion, etPass, etPassConfirm;
    private Button btnGuardar;
    private ImageView imgFoto;
    private String urlFoto = "", base64Foto = "";

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_registro);

        etEmpresa = findViewById(R.id.etAdminEmpresa);
        etDireccion = findViewById(R.id.etAdminDireccion);
        etTelefono = findViewById(R.id.etAdminTelefono);
        etCorreo = findViewById(R.id.etAdminCorreo);
        etDescripcion = findViewById(R.id.etAdminDescripcion);
        etPass = findViewById(R.id.etAdminPass);
        etPassConfirm = findViewById(R.id.etAdminPassConfirm);
        btnGuardar = findViewById(R.id.btnAdminGuardarRegistro);
        imgFoto = findViewById(R.id.ivAdminFoto);

        imgFoto.setOnClickListener(v -> elegirImagen());
        btnGuardar.setOnClickListener(v -> registrarFarmacia());
    }

    private void elegirImagen() {
        final CharSequence[] opciones = {"Tomar Foto", "Elegir de Galería", "Cancelar"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Selecciona el logo de la farmacia");
        builder.setItems(opciones, (dialog, item) -> {
            String seleccion = opciones[item].toString();
            if ("Tomar Foto".equals(seleccion)) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                } else {
                    tomarFoto();
                }
            } else if ("Elegir de Galería".equals(seleccion)) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, REQUEST_IMAGE_PICK);
            }
        });
        builder.show();
    }

    private void tomarFoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File fotoArchivo = crearArchivoImagen();
            if (fotoArchivo != null) {
                Uri uriFoto = FileProvider.getUriForFile(this, "com.example.conexionfarmaco.fileprovider", fotoArchivo);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uriFoto);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error cámara", Toast.LENGTH_SHORT).show();
        }
    }

    private File crearArchivoImagen() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("FARM_" + timeStamp + "_", ".jpg", storageDir);
        urlFoto = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                imgFoto.setPadding(0,0,0,0);
                procesarYMostrarImagen(Uri.fromFile(new File(urlFoto)));
            } else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    procesarYMostrarImagen(selectedImage);
                }
            }
        }
    }

    private void procesarYMostrarImagen(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
            
            // Redimensionar para optimizar memoria y red
            int maxSize = 500;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float ratio = (float) width / (float) height;
            if (ratio > 1) {
                width = maxSize;
                height = (int) (width / ratio);
            } else {
                height = maxSize;
                width = (int) (height * ratio);
            }
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
            
            imgFoto.setPadding(0,0,0,0);
            imgFoto.setImageBitmap(scaled);
            
            // Convertir a Base64
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] bytes = baos.toByteArray();
            base64Foto = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
            
        } catch (Exception e) {
            Log.e("AdminRegistro", "Error procesando imagen", e);
            Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
        }
    }

    private void registrarFarmacia() {
        String emp = etEmpresa.getText().toString().trim();
        String dir = etDireccion.getText().toString().trim();
        String tel = etTelefono.getText().toString().trim();
        String cor = etCorreo.getText().toString().trim();
        String des = etDescripcion.getText().toString().trim();
        String cla = etPass.getText().toString().trim();
        String con = etPassConfirm.getText().toString().trim();

        if (emp.isEmpty() || cor.isEmpty() || cla.isEmpty()) {
            Toast.makeText(this, "Complete los campos obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!cla.equals(con)) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String id = Utilidades.generarId();
                JSONObject json = new JSONObject();
                json.put("_id", id);
                json.put("empresa", emp);
                json.put("direccion", dir);
                json.put("telefono", tel);
                json.put("correo", cor);
                json.put("descripcion", des);
                json.put("clave", cla);
                json.put("foto", base64Foto);
                json.put("tipo", "farmacia");

                DBHelper dbHelper = new DBHelper(this);
                // Guardar localmente con la imagen en Base64 para que sea visible offline
                dbHelper.administrarFarmacias("nuevo", new String[]{id, emp, dir, tel, cor, cla, base64Foto, des});

                if (Utilidades.hayInternet(this)) {
                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(json.toString(), "POST", Utilidades.url_farmacias).get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.optBoolean("ok", false)) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Farmacia registrada con éxito. Inicia Sesión.", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(this, AdminLoginActivity.class));
                            finish();
                        });
                        return;
                    }
                }

                // Si no hay internet o falló el server
                dbHelper.agregarPendiente(Utilidades.url_farmacias, "POST", json.toString(), "couchdb");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Registro guardado localmente. Se sincronizará luego.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, AdminLoginActivity.class));
                    finish();
                });

            } catch (Exception e) {
                Log.e("AdminRegistro", "Error reg", e);
            }
        }).start();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            tomarFoto();
        }
    }
}
