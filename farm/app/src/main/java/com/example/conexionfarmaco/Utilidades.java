package com.example.conexionfarmaco;

import android.util.Base64;

public class Utilidades {
    static String url_consulta = "http://192.168.101.7:5984/usuarios/_design/usuarios/_view/usuarios";
    static String url_mto = "http://192.168.101.7:5984/usuarios";
    static String url_find = "http://192.168.101.7:5984/usuarios/_find";
    
    // Rutas para el sistema de Farmacias
    static String url_farmacias = "http://192.168.101.7:5984/farmacias";
    static String url_medicamentos = "http://192.168.101.7:5984/medicamentos";
    
    // Selectores CouchDB
    static String url_find_farmacias = "http://192.168.101.7:5984/farmacias/_find";
    static String url_find_medicamentos = "http://192.168.101.7:5984/medicamentos/_find";

    static String user = "steven";
    static String passwd = "200612";
    static String credencialesCodificadas = Base64.encodeToString((user + ":" + passwd).getBytes(), Base64.NO_WRAP);

    public static String generarId() {
        return java.util.UUID.randomUUID().toString();
    }
}
