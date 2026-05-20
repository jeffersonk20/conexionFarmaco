package com.example.conexionfarmaco;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
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

public class RegistroActivity extends AppCompatActivity {
    EditText txtNombres, txtApellidos, txtTelefono, txtCorreo, txtClave, txtConfirmar, txtDireccion, txtAlergias;
    Spinner spSangre, spEnfermedades;
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
        txtDireccion = findViewById(R.id.etDireccion);
        txtAlergias = findViewById(R.id.etAlergias);
        spSangre = findViewById(R.id.spTipoSangre);
        spEnfermedades = findViewById(R.id.spEnfermedades);

        configurarSpinners();

        btnGuardar = findViewById(R.id.btnGuardar);
        btnAtras = findViewById(R.id.btnAtras);
        imgFoto = findViewById(R.id.imgFotoRegistro);

        imgFoto.setOnClickListener(v -> elegirImagen());
        btnGuardar.setOnClickListener(v -> guardar());
        btnAtras.setOnClickListener(v -> finish());
    }

    private void configurarSpinners() {
        ArrayAdapter<CharSequence> adapterSangre = ArrayAdapter.createFromResource(this,
                R.array.tipos_sangre, R.layout.spinner_item);
        adapterSangre.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spSangre.setAdapter(adapterSangre);

        ArrayAdapter<CharSequence> adapterEnfermedades = ArrayAdapter.createFromResource(this,
                R.array.enfermedades, R.layout.spinner_item);
        adapterEnfermedades.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spEnfermedades.setAdapter(adapterEnfermedades);

        spEnfermedades.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                if (!selected.equals("Ninguna") && !selected.equals("Otras")) {
                    mostrarConsejoSalud(selected);
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void mostrarConsejoSalud(String enfermedad) {
        String consejo = "";
        switch (enfermedad) {
            case "Diabetes":
                consejo = "Te recomendaremos insulinas, metformina y equipos de monitoreo de glucosa.";
                break;
            case "Hipertensión":
                consejo = "Encontrarás antihipertensivos como Losartán y Enalapril en tu sección de recomendados.";
                break;
            case "Asma":
                consejo = "Podrás ver inhaladores y broncodilatadores sugeridos para tu condición.";
                break;
            case "Gastritis":
                consejo = "Te sugeriremos protectores gástricos y antiácidos efectivos.";
                break;
            case "Arritmia":
                consejo = "Verás medicamentos especializados para el cuidado del ritmo cardíaco.";
                break;
            case "Obesidad":
                consejo = "Te mostraremos complementos y opciones para el control de peso.";
                break;
            case "Hipotiroidismo":
                consejo = "Sugeriremos Levotiroxina y otros fármacos para el control tiroideo.";
                break;
        }
        
        if (!consejo.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("💡 Recomendación de Salud")
                    .setMessage("Al seleccionar " + enfermedad + ", personalizaremos tu pantalla de inicio con medicamentos adecuados para ti.\n\n" + consejo)
                    .setPositiveButton("Entendido", null)
                    .show();
        }
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
            File file = new File(storageDir, "REGISTRO_" + timeStamp + ".jpg");

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
                imgFoto.setImageURI(Uri.fromFile(file));
            }
        } catch (Exception e) {
            mostrar("Error al guardar imagen: " + e.getMessage());
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
            String dir = txtDireccion.getText().toString();
            String ale = txtAlergias.getText().toString();
            String san = spSangre.getSelectedItem().toString();
            String enf = spEnfermedades.getSelectedItem().toString();

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
            json.put("direccion", dir);
            json.put("alergias", ale);
            json.put("tipo_sangre", san);
            json.put("enfermedades", enf);
            json.put("tipo", "usuario");

            DBHelper dbHelper = new DBHelper(this);
            // Guardar localmente con TODOS los campos para que funcionen offline
            dbHelper.administrarUsuarios("nuevo", new String[]{id, "", nom, ape, tel, cor, cla, dir, ale, san, enf, urlFoto});

            if (Utilidades.hayInternet(this)) {
                new Thread(() -> {
                    try {
                        TareaServidor tarea = new TareaServidor();
                        String resServer = tarea.execute(json.toString(), "POST", Utilidades.url_mto).get();
                        JSONObject resJson = new JSONObject(resServer);
                        if (resJson.optBoolean("ok", false)) {
                            new MailManager(cor, nom).execute();
                        } else {
                            // Si falló el servidor pero hay internet, guardar como pendiente
                            dbHelper.agregarPendiente(Utilidades.url_mto, "POST", json.toString(), "couchdb");
                            JSONObject emailData = new JSONObject();
                            emailData.put("destinatario", cor);
                            emailData.put("asunto", "🏥 ¡Bienvenido/a a Conexión Fármaco!");
                            emailData.put("contenido", "Bienvenido " + nom);
                            dbHelper.agregarPendiente("", "", emailData.toString(), "email");
                        }
                    } catch (Exception e) {
                        dbHelper.agregarPendiente(Utilidades.url_mto, "POST", json.toString(), "couchdb");
                    }
                }).start();
            } else {
                // No hay internet, guardar ambos como pendientes
                dbHelper.agregarPendiente(Utilidades.url_mto, "POST", json.toString(), "couchdb");
                
                JSONObject emailData = new JSONObject();
                emailData.put("destinatario", cor);
                emailData.put("asunto", "🏥 ¡Bienvenido/a a Conexión Fármaco!");
                emailData.put("contenido", "Bienvenido " + nom);
                dbHelper.agregarPendiente("", "", emailData.toString(), "email");
                
                mostrar("Registro guardado localmente. Se sincronizará al conectar a internet.");
            }

            mostrar("¡Registro exitoso!");
            startActivity(new Intent(this, LoginActivity.class));
            finish();

        } catch (Exception e) {
            mostrar("Error de conexión: " + e.getMessage());
        }
    }

    private void mostrar(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
