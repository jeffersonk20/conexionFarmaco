package com.example.conexionfarmaco;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
    private String urlFoto = "";

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
                imgFoto.setImageURI(Uri.parse(urlFoto));
            } else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    guardarImagenLocalmente(selectedImage);
                }
            }
        }
    }

    private void guardarImagenLocalmente(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(storageDir, "LOGO_FARM_" + timeStamp + ".jpg");

            OutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int length;
            if (inputStream != null) {
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.close();
                inputStream.close();
                urlFoto = file.getAbsolutePath();
                imgFoto.setPadding(0,0,0,0);
                imgFoto.setImageURI(Uri.fromFile(file));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al guardar imagen", Toast.LENGTH_SHORT).show();
        }
    }

    private void registrarFarmacia() {
        String emp = etEmpresa.getText().toString();
        String dir = etDireccion.getText().toString();
        String tel = etTelefono.getText().toString();
        String cor = etCorreo.getText().toString();
        String des = etDescripcion.getText().toString();
        String cla = etPass.getText().toString();
        String con = etPassConfirm.getText().toString();

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
                json.put("foto", urlFoto);
                json.put("tipo", "farmacia");

                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(json.toString(), "POST", Utilidades.url_farmacias).get();
                
                JSONObject resJson = new JSONObject(res);
                if (resJson.optBoolean("ok", false)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Farmacia registrada con éxito. Inicia Sesión.", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(this, AdminLoginActivity.class));
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + res, Toast.LENGTH_LONG).show());
                }
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
