package com.example.conexionfarmaco;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class CarritoActivity extends AppCompatActivity {

    private LinearLayout containerItems;
    private JSONArray cartItems = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_carrito);

        containerItems = findViewById(R.id.containerItemsCarrito);
        
        findViewById(R.id.btnCarritoAtras).setOnClickListener(v -> finish());
        
        findViewById(R.id.btnComprarTodo).setOnClickListener(v -> {
            // Saltamos la simulación de tarjeta local y vamos directo a Facturación
            // El pago real se hará con Wompi al final del proceso
            Intent intent = new Intent(this, FacturacionActivity.class);
            intent.putExtra("metodo_pago", "tarjeta");
            startActivity(intent);
        });

        findViewById(R.id.btnReservarEfectivo).setOnClickListener(v -> {
            Intent intent = new Intent(this, FacturacionActivity.class);
            intent.putExtra("metodo_pago", "efectivo");
            startActivity(intent);
        });

        findViewById(R.id.btnVerHistorialDesdeCarrito).setOnClickListener(v -> {
            startActivity(new Intent(this, HistorialPedidosActivity.class));
        });

        cargarCarrito();
    }

    private void cargarCarrito() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("CartPrefs", MODE_PRIVATE);
            String cartStr = prefs.getString("cart", "[]");
            cartItems = new JSONArray(cartStr);

            if (cartItems.length() == 0) {
                // Opcional: mostrar un mensaje de carrito vacío
            }
            mostrarItems();
        } catch (Exception e) {}
    }

    private void guardarCarrito() {
        android.content.SharedPreferences prefs = getSharedPreferences("CartPrefs", MODE_PRIVATE);
        prefs.edit().putString("cart", cartItems.toString()).apply();
    }

    private void mostrarItems() {
        containerItems.removeAllViews();
        for (int i = 0; i < cartItems.length(); i++) {
            try {
                JSONObject item = cartItems.getJSONObject(i);
                View view = getLayoutInflater().inflate(R.layout.item_carrito, containerItems, false);
                
                TextView tvNombre = view.findViewById(R.id.tvItemCarritoNombre);
                TextView tvCant = view.findViewById(R.id.tvItemCarritoCantidad);
                ImageView ivFoto = view.findViewById(R.id.ivItemCarritoFoto);
                
                tvNombre.setText(item.getString("nombre"));
                tvCant.setText(String.valueOf(item.getInt("cantidad")));

                // Cargar foto si existe
                String foto = item.optString("foto1", "");
                if (!foto.isEmpty()) {
                    Utilidades.cargarImagenBase64(foto, ivFoto);
                }
                
                view.findViewById(R.id.btnSumar).setOnClickListener(v -> {
                    try {
                        item.put("cantidad", item.getInt("cantidad") + 1);
                        tvCant.setText(String.valueOf(item.getInt("cantidad")));
                        guardarCarrito();
                    } catch (Exception e) {}
                });

                view.findViewById(R.id.btnRestar).setOnClickListener(v -> {
                    try {
                        int cant = item.getInt("cantidad");
                        if (cant > 1) {
                            item.put("cantidad", cant - 1);
                            tvCant.setText(String.valueOf(item.getInt("cantidad")));
                            guardarCarrito();
                        }
                    } catch (Exception e) {}
                });

                int finalI = i;
                view.findViewById(R.id.btnEliminarItem).setOnClickListener(v -> {
                    containerItems.removeView(view);
                    // Eliminar del JSONArray
                    JSONArray newArray = new JSONArray();
                    for (int j = 0; j < cartItems.length(); j++) {
                        if (j != finalI) {
                            try {
                                newArray.put(cartItems.get(j));
                            } catch (Exception e) {}
                        }
                    }
                    cartItems = newArray;
                    guardarCarrito();
                    mostrarItems(); // Refrescar para actualizar los indices finalI
                });

                containerItems.addView(view);
            } catch (Exception e) {}
        }
    }
}
