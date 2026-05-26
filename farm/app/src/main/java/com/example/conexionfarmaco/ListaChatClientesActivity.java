package com.example.conexionfarmaco;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_chat_clientes);

        rv = findViewById(R.id.rvListaChatClientes);
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
        new Thread(() -> {
            try {
                DBHelper db = new DBHelper(this);
                
                // Intentar sincronizar del servidor primero para tener los últimos mensajes de clientes
                if (Utilidades.hayInternet(this)) {
                    JSONObject selector = new JSONObject();
                    JSONObject query = new JSONObject();
                    query.put("id_farmacia", farmaciaId);
                    query.put("tipo_doc", "mensaje");
                    selector.put("selector", query);

                    TareaServidor tarea = new TareaServidor();
                    String res = tarea.execute(selector.toString(), "POST", Utilidades.url_mto + "/_find").get();
                    JSONObject resJson = new JSONObject(res);
                    if (resJson.has("docs")) {
                        org.json.JSONArray docs = resJson.getJSONArray("docs");
                        for (int i = 0; i < docs.length(); i++) {
                            db.guardarMensajeLocal(docs.getJSONObject(i));
                        }
                    }
                }

                // Cargar desde cache agrupado
                List<JSONObject> chats = db.obtenerConversacionesFarmacia(farmaciaId);
                runOnUiThread(() -> {
                    listaConversaciones.clear();
                    listaConversaciones.addAll(chats);
                    adapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                Log.e("ChatList", "Error", e);
            }
        }).start();
    }

    class ChatClientesAdapter extends RecyclerView.Adapter<ChatClientesAdapter.VH> {
        private List<JSONObject> items;
        ChatClientesAdapter(List<JSONObject> items) { this.items = items; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            try {
                JSONObject obj = items.get(position);
                holder.t1.setText(obj.optString("nombre_cliente", "Cliente " + (position + 1)));
                holder.t2.setText(obj.optString("ultimo_mensaje", ""));
                
                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(ListaChatClientesActivity.this, ChatMensajeriaActivity.class);
                    intent.putExtra("id_farmacia", farmaciaId);
                    intent.putExtra("id_usuario", obj.optString("id_usuario"));
                    intent.putExtra("nombre_receptor", obj.optString("nombre_cliente"));
                    startActivity(intent);
                });
            } catch (Exception e) {}
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            VH(View v) {
                super(v);
                t1 = v.findViewById(android.R.id.text1);
                t2 = v.findViewById(android.R.id.text2);
                t1.setTextColor(getResources().getColor(R.color.azul_primario));
                t1.setTextSize(18);
            }
        }
    }
}
