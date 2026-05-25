package com.example.conexionfarmaco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
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
    private TextView tvCountdown;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Forzar el redimensionamiento de la ventana cuando aparezca el teclado
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_chat_ia);

        recyclerChat = findViewById(R.id.recycler_chat);
        editQuery = findViewById(R.id.edit_query);
        btnSend = findViewById(R.id.btn_send);
        progressLoading = findViewById(R.id.progress_loading);
        tvCountdown = findViewById(R.id.tv_countdown);

        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);
        recyclerChat.setLayoutManager(new LinearLayoutManager(this));
        recyclerChat.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());

        editQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Ajuste para que el teclado no tape el input y el recycler suba
        recyclerChat.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom) {
                recyclerChat.postDelayed(() -> {
                    if (messages.size() > 0) {
                        recyclerChat.smoothScrollToPosition(messages.size() - 1);
                    }
                }, 100);
            }
        });

        // Mensaje inicial de bienvenida
        addMessage("¡Hola! Soy tu asistente de Conexión Fármaco. ¿En qué puedo ayudarte hoy?", ChatMessage.TYPE_AI);
    }

    private void sendMessage() {
        String query = editQuery.getText().toString().trim();
        // Si el campo está vacío o si ya hay una petición en curso, no hacer nada
        if (query.isEmpty() || progressLoading.getVisibility() == View.VISIBLE) return;

        addMessage(query, ChatMessage.TYPE_USER);
        editQuery.setText("");
        progressLoading.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false); // Desactivar el botón para evitar múltiples clics

        Utilidades.consultarIA(this, query, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    btnSend.setEnabled(true); // Reactivar el botón
                    try {
                        String response = result.getText();
                        if (response != null && !response.isEmpty()) {
                            // Limpiar asteriscos y formato markdown que a veces envía la IA
                            response = response.replace("**", "").replace("*", "").trim();
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
                    btnSend.setEnabled(true); // Reactivar el botón para reintentar
                    
                    String errorDetail = t.getMessage() != null ? t.getMessage() : t.toString();
                    String finalMessage = "ERROR TÉCNICO: " + errorDetail;

                    // Detectar si es error de cuota y extraer tiempo
                    if (errorDetail.contains("quota") || errorDetail.contains("retry in")) {
                        finalMessage = "HAS EXCEDIDO EL LÍMITE DE PREGUNTAS POR AHORA.";
                        try {
                            // Intentar extraer los segundos (ej: "retry in 10.5s")
                            String secondsStr = errorDetail.split("retry in ")[1].split("s")[0].trim();
                            double seconds = Double.parseDouble(secondsStr);
                            iniciarContador((long)(seconds * 1000) + 1000); // +1s de margen
                        } catch (Exception e) {
                            iniciarContador(60000); // Fallback a 60 segundos si falla el parseo
                        }
                    }

                    addMessage(finalMessage, ChatMessage.TYPE_AI);
                    Log.e("GeminiError", "Fallo total: ", t);
                });
            }
        });
    }

    private void iniciarContador(long millis) {
        tvCountdown.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);
        
        new android.os.CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText("Límite excedido. Espera " + (millisUntilFinished / 1000) + "s para preguntar de nuevo");
            }

            @Override
            public void onFinish() {
                tvCountdown.setVisibility(View.GONE);
                btnSend.setEnabled(true);
            }
        }.start();
    }

    private void addMessage(String content, int type) {
        messages.add(new ChatMessage(content, type));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.scrollToPosition(messages.size() - 1);
    }
}
