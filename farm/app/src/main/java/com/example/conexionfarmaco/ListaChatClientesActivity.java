package com.example.conexionfarmaco;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ListaChatClientesActivity extends AppCompatActivity {

    private RecyclerView rv;
    private ChatClientesAdapter adapter;
    private List<JSONObject> listaConversaciones = new ArrayList<>();
    private String farmaciaId;
    private android.widget.ProgressBar pb;
    private TextView tvSinChats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_chat_clientes);

        rv = findViewById(R.id.rvListaChatClientes);
        pb = findViewById(R.id.pbListaChat);
        tvSinChats = findViewById(R.id.tvSinChats);
        
        farmaciaId = getSharedPreferences("AdminPrefs", MODE_PRIVATE).getString("farmaciaId", "");

        adapter = new ChatClientesAdapter(listaConversaciones);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        findViewById(R.id.btnBackListaChat).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarConversaciones();
    }

    private void cargarConversaciones() {
        if (listaConversaciones.isEmpty()) pb.setVisibility(View.VISIBLE);
        tvSinChats.setVisibility(View.GONE);
        
        new Thread(() -> {
            try {
                DBHelper db = new DBHelper(this);
                
                // 1. Mostrar cache local de inmediato para velocidad
                List<JSONObject> chatsIniciales = db.obtenerConversacionesFarmacia(farmaciaId);
                runOnUiThread(() -> {
                    listaConversaciones.clear();
                    listaConversaciones.addAll(chatsIniciales);
                    adapter.notifyDataSetChanged();
                    if (!chatsIniciales.isEmpty()) pb.setVisibility(View.GONE);
                });

                // 2. Sincronizar en segundo plano si hay internet
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    JSONObject query = new JSONObject();
                    query.put("id_farmacia", farmaciaId);
                    query.put("tipo_doc", "mensaje");
                    selector.put("selector", query);

                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_mto + "/_find").get();
                    if (res != null && !res.contains("Error")) {
                        JSONObject resJson = new JSONObject(res);
                        if (resJson.has("docs")) {
                            org.json.JSONArray docs = resJson.getJSONArray("docs");
                            for (int i = 0; i < docs.length(); i++) {
                                db.guardarMensajeLocal(docs.getJSONObject(i));
                            }
                            
                            // 3. Recargar tras sincronizar si hubo cambios
                            List<JSONObject> chatsFinales = db.obtenerConversacionesFarmacia(farmaciaId);
                            runOnUiThread(() -> {
                                pb.setVisibility(View.GONE);
                                listaConversaciones.clear();
                                listaConversaciones.addAll(chatsFinales);
                                adapter.notifyDataSetChanged();
                                if (chatsFinales.isEmpty()) tvSinChats.setVisibility(View.VISIBLE);
                                else tvSinChats.setVisibility(View.GONE);
                            });
                        }
                    }
                } else {
                    runOnUiThread(() -> {
                        pb.setVisibility(View.GONE);
                        if (listaConversaciones.isEmpty()) tvSinChats.setVisibility(View.VISIBLE);
                    });
                }

            } catch (Exception e) {
                Log.e("ChatList", "Error", e);
                runOnUiThread(() -> pb.setVisibility(View.GONE));
            }
        }).start();
    }

    class ChatClientesAdapter extends RecyclerView.Adapter<ChatClientesAdapter.VH> {
        private List<JSONObject> items;
        ChatClientesAdapter(List<JSONObject> items) { this.items = items; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_conversacion, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            try {
                JSONObject obj = items.get(position);
                holder.t1.setText(obj.optString("nombre_cliente", "Cliente " + (position + 1)));
                holder.t2.setText(obj.optString("ultimo_mensaje", ""));

                String foto = obj.optString("cliente_foto", "");
                if (!foto.isEmpty()) {
                    Utilidades.cargarImagenBase64(foto, holder.ivUser);
                } else {
                    holder.ivUser.setImageResource(android.R.drawable.ic_menu_myplaces);
                }
                
                int noLeidos = obj.optInt("no_leidos", 0);
                if (noLeidos > 0) {
                    holder.badge.setVisibility(View.VISIBLE);
                    holder.badge.setText(String.valueOf(noLeidos));
                    holder.t2.setTypeface(null, android.graphics.Typeface.BOLD);
                    holder.t2.setTextColor(android.graphics.Color.parseColor("#000000")); // Negro total para no leídos
                } else {
                    holder.badge.setVisibility(View.GONE);
                    holder.t2.setTypeface(null, android.graphics.Typeface.NORMAL);
                    holder.t2.setTextColor(android.graphics.Color.parseColor("#333333")); // Gris muy oscuro para leídos
                }
                
                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(ListaChatClientesActivity.this, ChatMensajeriaActivity.class);
                    intent.putExtra("id_farmacia", farmaciaId);
                    intent.putExtra("id_usuario", obj.optString("id_usuario"));
                    intent.putExtra("nombre_receptor", obj.optString("nombre_cliente"));
                    intent.putExtra("foto_receptor", obj.optString("cliente_foto"));
                    startActivity(intent);
                });
            } catch (Exception e) {}
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2, badge;
            ImageView ivUser;
            VH(View v) {
                super(v);
                t1 = v.findViewById(R.id.tvChatNombre);
                t2 = v.findViewById(R.id.tvChatUltimoMsg);
                badge = v.findViewById(R.id.tvChatBadge);
                ivUser = v.findViewById(R.id.ivUserIcon);
            }
        }
    }
}
