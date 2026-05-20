package com.example.conexionfarmaco;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;

public class TareaServidor extends AsyncTask<String, String, String> {

    @Override
    protected String doInBackground(String... params) {
        String jsonDatos = params[0];
        String metodo = params[1];
        String urlString = params[2];
        
        StringBuilder response = new StringBuilder();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(metodo);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Basic " + Utilidades.credencialesCodificadas);
            connection.setConnectTimeout(10000); // 10 segundos de espera
            connection.setReadTimeout(10000);

            // Solo enviar cuerpo si no es GET y si hay datos reales que enviar
            if (!metodo.equals("GET") && jsonDatos != null && !jsonDatos.isEmpty()) {
                connection.setDoOutput(true);
                Writer writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
                writer.write(jsonDatos);
                writer.close();
            }

            int responseCode = connection.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? 
                             connection.getInputStream() : connection.getErrorStream();

            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
            } else {
                response.append("{\"ok\":false, \"msg\":\"Código de error: ").append(responseCode).append("\"}");
            }

            return response.toString();
        } catch (Exception e) {
            return "{\"ok\":false, \"msg\":\"Error de red: " + e.getMessage() + "\"}";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
