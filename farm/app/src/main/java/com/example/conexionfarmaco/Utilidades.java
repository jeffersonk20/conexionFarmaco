package com.example.conexionfarmaco;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

public class Utilidades {
    static String url_consulta = "http://192.168.82.178:5984/usuarios/_design/usuarios/_view/usuarios";
    static String url_mto = "http://192.168.82.178:5984/usuarios";
    static String url_find = "http://192.168.82.178:5984/usuarios/_find";
    
    // Rutas para el sistema de Farmacias
    static String url_farmacias = "http://192.168.82.178:5984/farmacias";
    static String url_medicamentos = "http://192.168.82.178:5984/medicamentos";
    static String url_pedidos = "http://192.168.82.178:5984/pedidos";
    
    // Selectores CouchDB
    static String url_find_farmacias = "http://192.168.82.178:5984/farmacias/_find";
    static String url_find_medicamentos = "http://192.168.82.178:5984/medicamentos/_find";
    static String url_find_pedidos = "http://192.168.82.178:5984/pedidos/_find";

    static String user = "steven";
    static String passwd = "200612";
    static String credencialesCodificadas = Base64.encodeToString((user + ":" + passwd).getBytes(), Base64.NO_WRAP);

    public static String generarId() {
        return java.util.UUID.randomUUID().toString();
    }

    public static void cargarImagenBase64(String base64, ImageView iv) {
        if (base64 != null && !base64.isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                if (decodedByte != null) {
                    iv.setImageBitmap(decodedByte);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
