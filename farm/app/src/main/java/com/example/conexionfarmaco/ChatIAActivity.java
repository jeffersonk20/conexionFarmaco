package com.example.conexionfarmaco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import java.util.ArrayList;
import java.util.List;

public class ChatIAActivity extends AppCompatActivity {

    private RecyclerView recyclerChat;
    private ChatAdapter adapter;
    private List<ChatMessage> messages;
    private EditText editQuery;
    private ImageButton btnSend;
    private ProgressBar progressLoading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_ia);

        recyclerChat = findViewById(R.id.recycler_chat);
        editQuery = findViewById(R.id.edit_query);
        btnSend = findViewById(R.id.btn_send);
        progressLoading = findViewById(R.id.progress_loading);

        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);
        recyclerChat.setLayoutManager(new LinearLayoutManager(this));
        recyclerChat.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());

        // Mensaje inicial de bienvenida
        addMessage("¡Hola! Soy tu asistente de Conexión Fármaco. ¿En qué puedo ayudarte hoy?", ChatMessage.TYPE_AI);
    }

    private void sendMessage() {
        String query = editQuery.getText().toString().trim();
        if (query.isEmpty()) return;

        addMessage(query, ChatMessage.TYPE_USER);
        editQuery.setText("");
        progressLoading.setVisibility(View.VISIBLE);

        Utilidades.consultarIA(this, query, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    try {
                        String response = result.getText();
                        if (response != null && !response.isEmpty()) {
                            addMessage(response, ChatMessage.TYPE_AI);
                        } else {
                            addMessage("La IA no pudo generar una respuesta clara. Intenta reformular tu pregunta.", ChatMessage.TYPE_AI);
                        }
                    } catch (Exception e) {
                        addMessage("La respuesta fue bloqueada por seguridad o hubo un error al procesarla.", ChatMessage.TYPE_AI);
                        Log.e("GeminiError", "Error al obtener texto: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    // Mostramos el error real para diagnosticar
                    String errorDetail = t.getMessage() != null ? t.getMessage() : t.toString();
                    addMessage("ERROR TÉCNICO: " + errorDetail, ChatMessage.TYPE_AI);
                    Log.e("GeminiError", "Fallo total: ", t);
                });
            }
        });
    }

    private void addMessage(String content, int type) {
        messages.add(new ChatMessage(content, type));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.scrollToPosition(messages.size() - 1);
    }
}
