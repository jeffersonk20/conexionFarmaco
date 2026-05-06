package com.example.conexionfarmaco;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RegistroActivity extends AppCompatActivity {
    EditText txtNombres, txtApellidos, txtTelefono, txtCorreo, txtClave, txtConfirmar;
    Button btnGuardar, btnAtras;
    ImageView imgFoto;
    String urlFoto = "";

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        txtNombres = findViewById(R.id.etNombres);
        txtApellidos = findViewById(R.id.etApellidos);
        txtTelefono = findViewById(R.id.etTelefono);
        txtCorreo = findViewById(R.id.etCorreo);
        txtClave = findViewById(R.id.etPassword);
        txtConfirmar = findViewById(R.id.etConfirmarPassword);
        btnGuardar = findViewById(R.id.btnGuardar);
        btnAtras = findViewById(R.id.btnAtras);
        imgFoto = findViewById(R.id.imgFotoRegistro);

        imgFoto.setOnClickListener(v -> elegirImagen());
        btnGuardar.setOnClickListener(v -> guardar());
        btnAtras.setOnClickListener(v -> finish());
    }

    private void elegirImagen() {
        final CharSequence[] opciones = {"Tomar Foto", "Elegir de Galería", "Cancelar"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Selecciona una foto");
        builder.setItems(opciones, (dialog, item) -> {
            if (opciones[item].equals("Tomar Foto")) {
                comprobarPermisosCamara();
            } else if (opciones[item].equals("Elegir de Galería")) {
                abrirGaleria();
            } else {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void comprobarPermisosCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            tomarFoto();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tomarFoto();
            } else {
                mostrar("Se requiere permiso de cámara");
            }
        }
    }

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    private void tomarFoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File fotoArchivo = crearArchivoImagen();
            if (fotoArchivo != null) {
                Uri uriFoto = FileProvider.getUriForFile(this, "com.example.conexionfarmaco.fileprovider", fotoArchivo);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uriFoto);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        } catch (Exception e) {
            mostrar("Error: " + e.getMessage());
        }
    }

    private File crearArchivoImagen() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        urlFoto = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                imgFoto.setImageURI(Uri.parse(urlFoto));
            } else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    urlFoto = selectedImage.toString();
                    imgFoto.setImageURI(selectedImage);
                }
            }
        }
    }

    private void guardar() {
        try {
            String nom = txtNombres.getText().toString();
            String ape = txtApellidos.getText().toString();
            String tel = txtTelefono.getText().toString();
            String cor = txtCorreo.getText().toString();
            String cla = txtClave.getText().toString();
            String con = txtConfirmar.getText().toString();

            if (nom.isEmpty() || ape.isEmpty() || cor.isEmpty() || cla.isEmpty()) {
                mostrar("Complete los campos obligatorios");
                return;
            }

            if (!cla.equals(con)) {
                mostrar("Las contraseñas no coinciden");
                return;
            }

            String id = Utilidades.generarId();
            JSONObject json = new JSONObject();
            json.put("_id", id);
            json.put("nombres", nom);
            json.put("apellidos", ape);
            json.put("telefono", tel);
            json.put("correo", cor);
            json.put("clave", cla);
            json.put("foto", urlFoto);

            TareaServidor tarea = new TareaServidor();
            String resServer = tarea.execute(json.toString(), "POST", Utilidades.url_mto).get();
            
            JSONObject resJson = new JSONObject(resServer);
            if (resJson.has("ok") && resJson.getBoolean("ok")) {
                mostrar("¡Registro exitoso!");
                new MailManager(cor, nom).execute();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else {
                mostrar("Error al guardar en la nube: " + resServer);
            }
        } catch (Exception e) {
            mostrar("Error de conexión: " + e.getMessage());
        }
    }

    private void mostrar(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
