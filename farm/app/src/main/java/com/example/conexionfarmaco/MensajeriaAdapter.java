package com.example.conexionfarmaco;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.util.List;

public class MensajeriaAdapter extends RecyclerView.Adapter<MensajeriaAdapter.ViewHolder> {

    private List<JSONObject> mensajes;
    private String idPropio;

    public MensajeriaAdapter(List<JSONObject> mensajes, String idPropio) {
        this.mensajes = mensajes;
        this.idPropio = idPropio;
    }

    @Override
    public int getItemViewType(int position) {
        try {
            JSONObject msg = mensajes.get(position);
            return msg.getString("emisor").equals(idPropio) ? 0 : 1;
        } catch (Exception e) { return 1; }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Reutilizamos los layouts de Chat IA si existen, o creamos unos similares
        // viewType 0 = Propio (derecha), 1 = Otro (izquierda)
        int layout = (viewType == 0) ? R.layout.item_chat_user : R.layout.item_chat_ai;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject msg = mensajes.get(position);
            holder.tvMensaje.setText(msg.getString("mensaje"));
        } catch (Exception e) {}
    }

    @Override
    public int getItemCount() {
        return mensajes.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMensaje;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // item_chat_user y item_chat_ai usan R.id.text_message
            tvMensaje = itemView.findViewById(R.id.text_message);
        }
    }
}
