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
import android.view.View;
import android.widget.LinearLayout;
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
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PerfilActivity extends AppCompatActivity {

    private ImageView imgPerfil, btnEditarDatos, btnAgregarDireccion;
    private TextView tvNombre, tvCorreo, tvTelefono, tvCorreoDetalle, tvSinCompras, tvAlergias, tvTipoSangre, tvDireccionPrincipal, tvEnfermedades;
    private Button btnCambiarFoto, btnCerrarSesion;
    private LinearLayout containerHistorial;
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
        btnEditarDatos = findViewById(R.id.btnEditarDatos);
        containerHistorial = findViewById(R.id.containerHistorial);
        tvSinCompras = findViewById(R.id.tvSinCompras);
        tvAlergias = findViewById(R.id.tvAlergias);
        tvTipoSangre = findViewById(R.id.tvTipoSangre);
        tvDireccionPrincipal = findViewById(R.id.tvDireccionPrincipal);
        tvEnfermedades = findViewById(R.id.tvEnfermedades);
        btnAgregarDireccion = findViewById(R.id.btnAgregarDireccion);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        cargarDatosSeguros();
        cargarHistorial();

        if (btnCambiarFoto != null) btnCambiarFoto.setOnClickListener(v -> elegirImagen());
        if (btnCerrarSesion != null) btnCerrarSesion.setOnClickListener(v -> cerrarSesion());
        if (btnEditarDatos != null) btnEditarDatos.setOnClickListener(v -> mostrarDialogoEditar());
        if (btnAgregarDireccion != null) btnAgregarDireccion.setOnClickListener(v -> mostrarDialogoDireccion());
    }

    private void cargarHistorial() {
        if (tvSinCompras == null || containerHistorial == null) return;
        
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String savedData = prefs.getString("userData", "");
        if (savedData.isEmpty()) return;

        try {
            JSONObject user = new JSONObject(savedData);
            String userEmail = user.optString("correo", "");
            if (userEmail.isEmpty()) return;

            new Thread(() -> {
                try {
                    DBHelper db = new DBHelper(this);
                    List<JSONObject> pedidos;

                    if (Utilidades.hayInternet(this)) {
                        JSONObject selector = new JSONObject();
                        selector.put("selector", new JSONObject().put("cliente_correo", userEmail));
                        
                        TareaServidor tarea = new TareaServidor();
                        String res = tarea.execute(selector.toString(), "POST", Utilidades.url_find_pedidos).get();
                        JSONObject resJson = new JSONObject(res);
                        
                        if (resJson.has("docs")) {
                            JSONArray docs = resJson.getJSONArray("docs");
                            db.limpiarPedidosUsuario(userEmail);
                            for (int i = 0; i < docs.length(); i++) {
                                db.guardarPedidoLocal(docs.getJSONObject(i));
                            }
                        }
                    }

                    // Cargar de la base de datos local (que ya incluye lo que acabamos de bajar o lo que estaba offline)
                    pedidos = db.obtenerPedidosCache(userEmail);

                    runOnUiThread(() -> {
                        containerHistorial.removeAllViews();
                        if (pedidos.isEmpty()) {
                            tvSinCompras.setVisibility(View.VISIBLE);
                            tvSinCompras.setText("No tienes compras registradas");
                        } else {
                            tvSinCompras.setVisibility(View.GONE);
                            for (JSONObject pedido : pedidos) {
                                agregarItemHistorialResumido(pedido);
                            }
                        }
                    });

                } catch (Exception e) {
                    Log.e("Perfil", "Error historial", e);
                }
            }).start();

        } catch (Exception e) {}
    }

    private void agregarItemHistorialResumido(JSONObject pedido) {
        try {
            View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, containerHistorial, false);
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);

            String fecha = pedido.optString("fecha", "Sin fecha");
            String estado = pedido.optString("estado", "Pendiente");
            JSONArray items = pedido.getJSONArray("items");
            
            text1.setText("Pedido del " + fecha);
            text1.setTextColor(getResources().getColor(R.color.azul_primario));
            
            text2.setText(items.length() + " productos - Estado: " + estado);
            
            view.setPadding(0, 20, 0, 20);
            view.setOnClickListener(v -> {
                startActivity(new Intent(this, HistorialPedidosActivity.class));
            });

            containerHistorial.addView(view);
        } catch (Exception e) {}
    }


    private void mostrarDialogoEditar() {
        if (userData == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Información");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final android.widget.EditText inputNombre = new android.widget.EditText(this);
        inputNombre.setHint("Nombres");
        inputNombre.setText(userData.optString("nombres", ""));
        layout.addView(inputNombre);

        final android.widget.EditText inputApellido = new android.widget.EditText(this);
        inputApellido.setHint("Apellidos");
        inputApellido.setText(userData.optString("apellidos", ""));
        layout.addView(inputApellido);

        final android.widget.EditText inputTel = new android.widget.EditText(this);
        inputTel.setHint("Teléfono");
        inputTel.setText(userData.optString("telefono", ""));
        layout.addView(inputTel);

        final android.widget.EditText inputAlergias = new android.widget.EditText(this);
        inputAlergias.setHint("Alergias (ej: Penicilina)");
        inputAlergias.setText(userData.optString("alergias", ""));
        layout.addView(inputAlergias);

        final android.widget.EditText inputSangre = new android.widget.EditText(this);
        inputSangre.setHint("Tipo de Sangre");
        inputSangre.setText(userData.optString("tipo_sangre", ""));
        layout.addView(inputSangre);

        final android.widget.EditText inputEnfermedades = new android.widget.EditText(this);
        inputEnfermedades.setHint("Enfermedades Crónicas");
        inputEnfermedades.setText(userData.optString("enfermedades", ""));
        layout.addView(inputEnfermedades);

        builder.setView(layout);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            actualizarDatosServidor(
                inputNombre.getText().toString(),
                inputApellido.getText().toString(),
                inputTel.getText().toString(),
                inputAlergias.getText().toString(),
                inputSangre.getText().toString(),
                inputEnfermedades.getText().toString()
            );
        });
        builder.setNegativeButton("Cancelar", (dialog, id) -> dialog.cancel());

        builder.show();
    }

    private void mostrarDialogoDireccion() {
        if (userData == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Agregar Dirección");

        final android.widget.EditText inputDir = new android.widget.EditText(this);
        inputDir.setHint("Ej: Calle Principal #123, Colonia Escalón");
        inputDir.setText(userData.optString("direccion", ""));
        builder.setView(inputDir);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            guardarDireccionServidor(inputDir.getText().toString());
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void guardarDireccionServidor(String dir) {
        new Thread(() -> {
            try {
                userData.put("direccion", dir);
                actualizarDocumentoEnCouch();
            } catch (Exception e) {
                Log.e("Perfil", "Error dir", e);
            }
        }).start();
    }

    private void actualizarDatosServidor(String nom, String ape, String tel, String alergias, String sangre, String enfermedades) {
        if (userData == null) return;
        
        new Thread(() -> {
            try {
                userData.put("nombres", nom);
                userData.put("apellidos", ape);
                userData.put("telefono", tel);
                userData.put("alergias", alergias);
                userData.put("tipo_sangre", sangre);
                userData.put("enfermedades", enfermedades);
                
                actualizarDocumentoEnCouch();
            } catch (Exception e) {
                Log.e("Perfil", "Error editando", e);
            }
        }).start();
    }

    private void actualizarDocumentoEnCouch() {
        try {
            String id = userData.optString("_id", "");
            String urlUpdate = Utilidades.url_mto + "/" + id;
            
            DBHelper dbHelper = new DBHelper(this);
            // Actualizar localmente siempre para que persista offline
            dbHelper.administrarUsuarios("modificar", new String[]{
                    id,
                    userData.optString("nombres"),
                    userData.optString("apellidos"),
                    userData.optString("telefono"),
                    userData.optString("correo"),
                    userData.optString("clave"), // Necesitamos mantener la clave
                    userData.optString("direccion"),
                    userData.optString("alergias"),
                    userData.optString("tipo_sangre"),
                    userData.optString("enfermedades"),
                    userData.optString("foto")
            });

            // Guardar en Prefs para el UI inmediato
            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit()
                    .putString("userData", userData.toString()).apply();

            if (Utilidades.hayInternet(this)) {
                TareaServidor tarea = new TareaServidor();
                String res = tarea.execute(userData.toString(), "PUT", urlUpdate).get();
                JSONObject resJson = new JSONObject(res);
                if (resJson.optBoolean("ok", false)) {
                    userData.put("_rev", resJson.getString("rev"));
                    getSharedPreferences("UserPrefs", MODE_PRIVATE).edit()
                            .putString("userData", userData.toString()).apply();
                } else {
                    dbHelper.agregarPendiente(urlUpdate, "PUT", userData.toString(), "couchdb");
                }
            } else {
                dbHelper.agregarPendiente(urlUpdate, "PUT", userData.toString(), "couchdb");
                runOnUiThread(() -> Toast.makeText(this, "Cambios guardados localmente", Toast.LENGTH_SHORT).show());
            }

            runOnUiThread(() -> actualizarUI(userData.toString()));
            
        } catch (Exception e) {
            Log.e("Perfil", "Error sync", e);
        }
    }

    private void cargarDatosSeguros() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String savedData = prefs.getString("userData", "");
        
        if (!savedData.isEmpty()) {
            try {
                userData = new JSONObject(savedData);
                actualizarUI(savedData);
            } catch (Exception e) {}
        }

        if (Utilidades.hayInternet(this)) {
            new Thread(() -> {
                try {
                    String correo = userData != null ? userData.optString("correo", "") : "";
                    if (correo.isEmpty()) return;

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
                            
                            // Sincronizar también con la DB local lo que venga de la nube
                            DBHelper db = new DBHelper(this);
                            db.administrarUsuarios("modificar", new String[]{
                                    userData.getString("_id"),
                                    userData.getString("nombres"),
                                    userData.getString("apellidos"),
                                    userData.getString("telefono"),
                                    userData.getString("correo"),
                                    userData.optString("clave", ""),
                                    userData.optString("direccion", ""),
                                    userData.optString("alergias", ""),
                                    userData.optString("tipo_sangre", ""),
                                    userData.optString("enfermedades", ""),
                                    userData.optString("foto", "")
                            });

                            runOnUiThread(() -> actualizarUI(actualizado));
                        }
                    }
                } catch (Exception e) {
                    Log.e("Perfil", "Error red", e);
                }
            }).start();
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
            if (tvAlergias != null) tvAlergias.setText(json.optString("alergias", "Ninguna registrada"));
            if (tvTipoSangre != null) tvTipoSangre.setText(json.optString("tipo_sangre", "No especificado"));
            if (tvEnfermedades != null) tvEnfermedades.setText(json.optString("enfermedades", "Ninguna registrada"));
            
            if (tvDireccionPrincipal != null) {
                String dir = json.optString("direccion", "No has agregado direcciones");
                tvDireccionPrincipal.setText(dir);
            }

            if (!fotoPath.isEmpty() && imgPerfil != null) {
                Utilidades.cargarImagenBase64(fotoPath, imgPerfil);
            }
        } catch (Exception e) {
            Log.e("Perfil", "Error UI", e);
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
                Utilidades.cargarImagenBase64(urlFoto, imgPerfil);
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
            Utilidades.cargarImagenBase64(urlFoto, imgPerfil);
        } catch (Exception e) {
            Log.e("Perfil", "Error guardando local", e);
        }
    }

    private void subirFoto() {
        if (userData == null) return;
        
        new Thread(() -> {
            try {
                userData.put("foto", urlFoto);
                actualizarDocumentoEnCouch();
                runOnUiThread(() -> Toast.makeText(this, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e("Perfil", "Error subida", e);
            }
        }).start();
    }


    private void cerrarSesion() {
        // Solo eliminar la sesión activa, NO los datos de biometría (lastUserData)
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit()
                .remove("userData")
                .apply();

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
