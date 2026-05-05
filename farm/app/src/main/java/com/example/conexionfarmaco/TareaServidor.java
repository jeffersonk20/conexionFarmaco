package com.example.conexionfarmaco;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;

public class TareaServidor extends AsyncTask<String, String, String> {

    @Override
    protected String doInBackground(String... params) {
        String jsonDatos = params[0]; // Datos JSON
        String metodo = params[1];    // POST, GET, PUT, DELETE
        String urlString = params[2]; // URL
        
        StringBuilder response = new StringBuilder();
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(metodo);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Basic " + Utilidades.credencialesCodificadas);

            if (!metodo.equals("GET")) {
                connection.setDoOutput(true);
                Writer writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
                writer.write(jsonDatos);
                writer.close();
            }

            BufferedReader reader;
            if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
            return response.toString();
        } catch (Exception e) {
            return "{\"ok\":false, \"msg\":\"Error de red: " + e.getMessage() + "\"}";
        }
    }
}
