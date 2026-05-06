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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PerfilActivity extends AppCompatActivity {

    private ImageView imgPerfil;
    private TextView tvNombre, tvCorreo, tvTelefono, tvCorreoDetalle;
    private Button btnCambiarFoto, btnCerrarSesion;
    private String urlFoto = "";
    private JSONObject userData;

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        imgPerfil = findViewById(R.id.imgPerfil);
        tvNombre = findViewById(R.id.tvNombreCompleto);
        tvCorreo = findViewById(R.id.tvCorreoPerfil);
        tvTelefono = findViewById(R.id.tvTelefonoPerfil);
        tvCorreoDetalle = findViewById(R.id.tvCorreoDetalle);
        btnCambiarFoto = findViewById(R.id.btnCambiarFoto);
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        cargarDatosSeguros();

        if (btnCambiarFoto != null) btnCambiarFoto.setOnClickListener(v -> elegirImagen());
        if (btnCerrarSesion != null) btnCerrarSesion.setOnClickListener(v -> cerrarSesion());
    }

    private void cargarDatosSeguros() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String savedData = prefs.getString("userData", "");
        
        if (!savedData.isEmpty()) {
            actualizarUI(savedData);
        }

        try {
            String correoABuscar = "";
            if (!savedData.isEmpty()) {
                correoABuscar = new JSONObject(savedData).optString("correo", "");
            }

            if (!correoABuscar.isEmpty()) {
                final String correo = correoABuscar;
                new Thread(() -> {
                    try {
                        JSONObject selector = new JSONObject();
                        JSONObject query = new JSONObject();
                        query.put("correo", correo);
                        selector.put("selector", query);
                        
                        TareaServidor tarea = new TareaServidor();
                        String respuesta = tarea.execute(selector.toString(), "POST", Utilidades.url_find).get();
                        
                        JSONObject resJson = new JSONObject(respuesta);
                        if (resJson.has("docs")) {
                            JSONArray docs = resJson.getJSONArray("docs");
                            if (docs.length() > 0) {
                                userData = docs.getJSONObject(0);
                                String actualizado = userData.toString();
                                prefs.edit().putString("userData", actualizado).apply();
                                runOnUiThread(() -> actualizarUI(actualizado));
                            }
                        }
                    } catch (Exception e) {
                        Log.e("Perfil", "Error red", e);
                    }
                }).start();
            }
        } catch (Exception e) {
            Log.e("Perfil", "Error parse", e);
        }
    }

    private void actualizarUI(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            String nom = json.optString("nombres", "Usuario");
            String ape = json.optString("apellidos", "");
            String cor = json.optString("correo", "Sin correo");
            String tel = json.optString("telefono", "Sin teléfono");
            String fotoPath = json.optString("foto", "");

            if (tvNombre != null) tvNombre.setText(nom + " " + ape);
            if (tvCorreo != null) tvCorreo.setText(cor);
            if (tvCorreoDetalle != null) tvCorreoDetalle.setText(cor);
            if (tvTelefono != null) tvTelefono.setText(tel);

            if (!fotoPath.isEmpty() && imgPerfil != null) {
                cargarImagenDesdeRuta(fotoPath);
            }
        } catch (Exception e) {
            Log.e("Perfil", "Error UI", e);
        }
    }

    private void cargarImagenDesdeRuta(String ruta) {
        try {
            if (ruta.startsWith("content://")) {
                imgPerfil.setImageURI(Uri.parse(ruta));
            } else {
                File file = new File(ruta);
                if (file.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    imgPerfil.setImageBitmap(bitmap);
                } else {
                    imgPerfil.setImageResource(R.mipmap.ic_launcher);
                }
            }
        } catch (Exception e) {
            imgPerfil.setImageResource(R.mipmap.ic_launcher);
        }
    }

    private void elegirImagen() {
        final CharSequence[] opciones = {"Tomar Foto", "Elegir de Galería", "Cancelar"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Actualizar foto de perfil");
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
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        urlFoto = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                // urlFoto ya tiene la ruta del archivo creado
                subirFoto();
                cargarImagenDesdeRuta(urlFoto);
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
            File file = new File(storageDir, "PERFIL_" + timeStamp + ".jpg");
            
            OutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            
            urlFoto = file.getAbsolutePath();
            subirFoto();
            cargarImagenDesdeRuta(urlFoto);
        } catch (Exception e) {
            Log.e("Perfil", "Error guardando local", e);
        }
    }

    private void subirFoto() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String savedData = prefs.getString("userData", "");
        if (savedData.isEmpty()) return;

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(savedData);
                json.put("foto", urlFoto);
                String id = json.optString("_id", "");
                if (id.isEmpty()) return;

                String urlUpdate = Utilidades.url_mto + "/" + id;
                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(json.toString(), "PUT", urlUpdate).get();
                
                JSONObject resJson = new JSONObject(res);
                if (resJson.optBoolean("ok", false)) {
                    json.put("_rev", resJson.getString("rev"));
                    prefs.edit().putString("userData", json.toString()).apply();
                    runOnUiThread(() -> Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e("Perfil", "Error subida", e);
            }
        }).start();
    }

    private void cerrarSesion() {
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            tomarFoto();
        }
    }
}
