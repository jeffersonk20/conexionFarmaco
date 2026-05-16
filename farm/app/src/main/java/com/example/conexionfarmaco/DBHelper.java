package com.example.conexionfarmaco;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "conexion_farmaco.db";
    private static final int DATABASE_VERSION = 10;

    public DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE usuarios (id TEXT PRIMARY KEY, nombres TEXT, apellidos TEXT, telefono TEXT, correo TEXT, clave TEXT, " +
                "direccion TEXT, alergias TEXT, tipo_sangre TEXT, enfermedades TEXT, foto TEXT)");
        db.execSQL("CREATE TABLE farmacias (id TEXT PRIMARY KEY, empresa TEXT, direccion TEXT, telefono TEXT, correo TEXT, clave TEXT, foto TEXT, descripcion TEXT)");
        db.execSQL("CREATE TABLE medicamentos (id TEXT PRIMARY KEY, id_farmacia TEXT, nombre TEXT, precio TEXT, stock TEXT, presentacion TEXT, promocion INTEGER, foto1 TEXT, nombre_farmacia TEXT)");
        db.execSQL("CREATE TABLE pedidos (id TEXT PRIMARY KEY, cliente_correo TEXT, cliente_nombre TEXT, cliente_direccion TEXT, cliente_telefono TEXT, items TEXT, total TEXT, fecha TEXT, estado TEXT, metodo_pago TEXT, farmacias_ids TEXT)");
        db.execSQL("CREATE TABLE pendientes (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, metodo TEXT, json TEXT, tipo TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS usuarios");
        db.execSQL("DROP TABLE IF EXISTS farmacias");
        db.execSQL("DROP TABLE IF EXISTS medicamentos");
        db.execSQL("DROP TABLE IF EXISTS pedidos");
        db.execSQL("DROP TABLE IF EXISTS pendientes");
        onCreate(db);
    }

    public long agregarPendiente(String url, String metodo, String json, String tipo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("url", url);
        values.put("metodo", metodo);
        values.put("json", json);
        values.put("tipo", tipo);
        return db.insert("pendientes", null, values);
    }

    public List<JSONObject> obtenerPendientes() {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM pendientes", null);
        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("id_local", cursor.getString(0));
                    obj.put("url", cursor.getString(1));
                    obj.put("metodo", cursor.getString(2));
                    obj.put("json", cursor.getString(3));
                    obj.put("tipo", cursor.getString(4));
                    lista.add(obj);
                } catch (Exception e) { e.printStackTrace(); }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return lista;
    }

    public void eliminarPendiente(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("pendientes", "id = ?", new String[]{id});
    }

    public void guardarFarmaciaCache(JSONObject farm) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("id", farm.getString("_id"));
            cv.put("empresa", farm.getString("empresa"));
            cv.put("direccion", farm.optString("direccion", ""));
            cv.put("telefono", farm.optString("telefono", ""));
            cv.put("correo", farm.optString("correo", ""));
            cv.put("foto", farm.optString("foto", ""));
            cv.put("descripcion", farm.optString("descripcion", ""));
            db.insertWithOnConflict("farmacias", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {}
    }

    public List<JSONObject> obtenerFarmaciasCache() {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM farmacias", null);
        if (c.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("_id", c.getString(0));
                    obj.put("empresa", c.getString(1));
                    obj.put("direccion", c.getString(2));
                    obj.put("telefono", c.getString(3));
                    obj.put("correo", c.getString(4));
                    obj.put("foto", c.getString(6));
                    obj.put("descripcion", c.getString(7));
                    lista.add(obj);
                } catch (Exception e) {}
            } while (c.moveToNext());
        }
        c.close();
        return lista;
    }

    public void guardarMedicamentoCache(JSONObject med) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("id", med.getString("_id"));
            cv.put("id_farmacia", med.optString("id_farmacia", ""));
            cv.put("nombre", med.optString("nombre", ""));
            cv.put("precio", med.optString("precio", "0"));
            cv.put("stock", med.optString("stock", "0"));
            cv.put("presentacion", med.optString("presentacion", ""));
            cv.put("promocion", med.optBoolean("promocion", false) ? 1 : 0);
            cv.put("foto1", med.optString("foto1", ""));
            cv.put("nombre_farmacia", med.optString("nombre_farmacia", ""));
            db.insertWithOnConflict("medicamentos", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {}
    }

    public void guardarMedicamentoLocal(JSONObject med) {
        guardarMedicamentoCache(med);
    }

    public void eliminarMedicamentoLocal(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("medicamentos", "id = ?", new String[]{id});
    }

    public List<JSONObject> obtenerMedicamentosCache(String query, boolean soloPromos) {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT * FROM medicamentos";
        
        if (soloPromos) {
            sql += " WHERE promocion = 1";
        } else if (query != null && !query.isEmpty()) {
            // Manejar múltiples términos de búsqueda (separados por | o espacios)
            String[] terms = query.replace("(?i)", "").split("\\|");
            sql += " WHERE ";
            for (int i = 0; i < terms.length; i++) {
                String term = terms[i].trim();
                if (term.isEmpty()) continue;
                sql += "(nombre LIKE '%" + term + "%' OR presentacion LIKE '%" + term + "%')";
                if (i < terms.length - 1) sql += " OR ";
            }
        }

        Cursor cursor = db.rawQuery(sql, null);
        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("_id", cursor.getString(0));
                    obj.put("id_farmacia", cursor.getString(1));
                    obj.put("nombre", cursor.getString(2));
                    obj.put("precio", cursor.getString(3));
                    obj.put("stock", cursor.getString(4));
                    obj.put("presentacion", cursor.getString(5));
                    obj.put("foto1", cursor.getString(7));
                    obj.put("nombre_farmacia", cursor.getString(8));
                    lista.add(obj);
                } catch (Exception e) {}
            } while (cursor.moveToNext());
        }
        cursor.close();
        return lista;
    }

    public void guardarPedidoLocal(JSONObject p) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("id", p.getString("_id"));
            cv.put("cliente_correo", p.optString("cliente_correo", ""));
            cv.put("cliente_nombre", p.optString("cliente_nombre", ""));
            cv.put("cliente_direccion", p.optString("cliente_direccion", ""));
            cv.put("cliente_telefono", p.optString("cliente_telefono", ""));
            cv.put("items", p.opt("items").toString());
            cv.put("total", p.optString("total", "0"));
            cv.put("fecha", p.optString("fecha", ""));
            cv.put("estado", p.optString("estado", "Pendiente"));
            cv.put("metodo_pago", p.optString("metodo_pago", ""));
            cv.put("farmacias_ids", p.optString("farmacias_ids", "[]"));
            db.insertWithOnConflict("pedidos", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {}
    }

    public List<JSONObject> obtenerPedidosCache(String correo) {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM pedidos WHERE cliente_correo=? ORDER BY fecha DESC", new String[]{correo});
        if (c.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("_id", c.getString(0));
                    obj.put("cliente_correo", c.getString(1));
                    obj.put("cliente_nombre", c.getString(2));
                    obj.put("cliente_direccion", c.getString(3));
                    obj.put("cliente_telefono", c.getString(4));
                    obj.put("items", new JSONArray(c.getString(5)));
                    obj.put("total", c.getString(6));
                    obj.put("fecha", c.getString(7));
                    obj.put("estado", c.getString(8));
                    obj.put("metodo_pago", c.getString(9));
                    lista.add(obj);
                } catch (Exception e) {}
            } while (c.moveToNext());
        }
        c.close();
        return lista;
    }

    public List<JSONObject> obtenerPedidosAdminCache() {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM pedidos ORDER BY fecha DESC", null);
        if (c.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("_id", c.getString(0));
                    obj.put("cliente_correo", c.getString(1));
                    obj.put("cliente_nombre", c.getString(2));
                    obj.put("cliente_direccion", c.getString(3));
                    obj.put("cliente_telefono", c.getString(4));
                    obj.put("items", new JSONArray(c.getString(5)));
                    obj.put("total", c.getString(6));
                    obj.put("fecha", c.getString(7));
                    obj.put("estado", c.getString(8));
                    obj.put("metodo_pago", c.getString(9));
                    obj.put("farmacias_ids", new JSONArray(c.getString(10)));
                    lista.add(obj);
                } catch (Exception e) {}
            } while (c.moveToNext());
        }
        c.close();
        return lista;
    }

    public void administrarUsuarios(String accion, String[] datos) {
        SQLiteDatabase db = getWritableDatabase();
        if (accion.equals("nuevo")) {
            db.execSQL("INSERT INTO usuarios(id, nombres, apellidos, telefono, correo, clave, direccion, alergias, tipo_sangre, enfermedades, foto) VALUES(?,?,?,?,?,?,?,?,?,?,?)", datos);
        } else if (accion.equals("modificar")) {
            db.execSQL("UPDATE usuarios SET nombres=?, apellidos=?, telefono=?, correo=?, clave=?, direccion=?, alergias=?, tipo_sangre=?, enfermedades=?, foto=? WHERE id=?", 
                new String[]{datos[1], datos[2], datos[3], datos[4], datos[5], datos[6], datos[7], datos[8], datos[9], datos[10], datos[0]});
        }
    }

    public void administrarFarmacias(String accion, String[] datos) {
        SQLiteDatabase db = getWritableDatabase();
        if (accion.equals("nuevo")) {
            db.execSQL("INSERT INTO farmacias(id, empresa, direccion, telefono, correo, clave, foto, descripcion) VALUES(?,?,?,?,?,?,?,?)", datos);
        } else if (accion.equals("modificar")) {
            db.execSQL("UPDATE farmacias SET empresa=?, direccion=?, telefono=?, correo=?, clave=?, foto=?, descripcion=? WHERE id=?", 
                new String[]{datos[1], datos[2], datos[3], datos[4], datos[5], datos[6], datos[7], datos[0]});
        }
    }

    public Cursor login(String correo, String clave) {
        return getReadableDatabase().rawQuery("SELECT * FROM usuarios WHERE correo=? AND clave=?", new String[]{correo, clave});
    }

    public Cursor loginFarmacia(String correo, String clave) {
        return getReadableDatabase().rawQuery("SELECT * FROM farmacias WHERE correo=? AND clave=?", new String[]{correo, clave});
    }

    public JSONObject obtenerFarmaciaLocal(String id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM farmacias WHERE id=?", new String[]{id});
        if (c.moveToFirst()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("_id", c.getString(0));
                obj.put("empresa", c.getString(1));
                obj.put("direccion", c.getString(2));
                obj.put("telefono", c.getString(3));
                obj.put("correo", c.getString(4));
                obj.put("clave", c.getString(5));
                obj.put("foto", c.getString(6));
                obj.put("descripcion", c.getString(7));
                c.close();
                return obj;
            } catch (Exception e) {}
        }
        c.close();
        return null;
    }
}
