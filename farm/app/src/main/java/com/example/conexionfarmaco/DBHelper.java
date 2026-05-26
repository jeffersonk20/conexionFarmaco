package com.example.conexionfarmaco;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "conexion_farmaco.db";
    private static final int DATABASE_VERSION = 16;

    public DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE usuarios (id TEXT PRIMARY KEY, rev TEXT, nombres TEXT, apellidos TEXT, telefono TEXT, correo TEXT, clave TEXT, " +
                "direccion TEXT, alergias TEXT, tipo_sangre TEXT, enfermedades TEXT, foto TEXT)");
        db.execSQL("CREATE TABLE farmacias (id TEXT PRIMARY KEY, rev TEXT, empresa TEXT, direccion TEXT, telefono TEXT, correo TEXT, clave TEXT, foto TEXT, descripcion TEXT, chat_habilitado INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE medicamentos (id TEXT PRIMARY KEY, rev TEXT, id_farmacia TEXT, nombre TEXT, precio TEXT, stock TEXT, presentacion TEXT, promocion INTEGER, foto1 TEXT, foto2 TEXT, foto3 TEXT, nombre_farmacia TEXT, enfermedad_objetivo TEXT, is_deleted INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE pedidos (id TEXT PRIMARY KEY, rev TEXT, cliente_correo TEXT, cliente_nombre TEXT, cliente_direccion TEXT, cliente_telefono TEXT, items TEXT, total TEXT, fecha TEXT, estado TEXT, metodo_pago TEXT, farmacias_ids TEXT)");
        db.execSQL("CREATE TABLE mensajes (id TEXT PRIMARY KEY, rev TEXT, emisor TEXT, receptor TEXT, mensaje TEXT, fecha TEXT, id_farmacia TEXT, id_usuario TEXT, leido INTEGER DEFAULT 0, emisor_nombre TEXT, cliente_nombre_completo TEXT)");
        db.execSQL("CREATE TABLE pendientes (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, metodo TEXT, json TEXT, tipo TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 11) { try { db.execSQL("ALTER TABLE medicamentos ADD COLUMN enfermedad_objetivo TEXT"); } catch(Exception e){} }
        if (oldVersion < 12) {
            try { db.execSQL("ALTER TABLE usuarios ADD COLUMN rev TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE farmacias ADD COLUMN rev TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE medicamentos ADD COLUMN rev TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE pedidos ADD COLUMN rev TEXT"); } catch (Exception e) {}
        }
        if (oldVersion < 13) {
            try { db.execSQL("ALTER TABLE medicamentos ADD COLUMN foto2 TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE medicamentos ADD COLUMN foto3 TEXT"); } catch (Exception e) {}
        }
        if (oldVersion < 14) {
            try { db.execSQL("ALTER TABLE medicamentos ADD COLUMN is_deleted INTEGER DEFAULT 0"); } catch (Exception e) {}
        }
        if (oldVersion < 15) {
            try { db.execSQL("ALTER TABLE farmacias ADD COLUMN chat_habilitado INTEGER DEFAULT 0"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE mensajes (id TEXT PRIMARY KEY, rev TEXT, emisor TEXT, receptor TEXT, mensaje TEXT, fecha TEXT, id_farmacia TEXT, id_usuario TEXT, leido INTEGER DEFAULT 0, emisor_nombre TEXT, cliente_nombre_completo TEXT)"); } catch (Exception e) {}
        }
        if (oldVersion < 16) {
            try { db.execSQL("ALTER TABLE mensajes ADD COLUMN cliente_nombre_completo TEXT"); } catch (Exception e) {}
        }
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
                    obj.put("id_local", cursor.getString(cursor.getColumnIndexOrThrow("id")));
                    obj.put("url", cursor.getString(cursor.getColumnIndexOrThrow("url")));
                    obj.put("metodo", cursor.getString(cursor.getColumnIndexOrThrow("metodo")));
                    obj.put("json", cursor.getString(cursor.getColumnIndexOrThrow("json")));
                    obj.put("tipo", cursor.getString(cursor.getColumnIndexOrThrow("tipo")));
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
        guardarFarmaciaCache(farm, false);
    }

    public void guardarFarmaciaCache(JSONObject farm, boolean forzar) {
        try {
            String id = farm.getString("_id");
            
            // ESCUDO: No sobreescribir si hay cambios locales pendientes, a menos que sea forzado (ej: desde SyncWorker)
            if (!forzar && estaPendienteSincronizacion(id)) {
                return;
            }

            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("id", id);
            cv.put("rev", farm.optString("_rev", ""));
            cv.put("empresa", farm.getString("empresa"));
            cv.put("direccion", farm.optString("direccion", ""));
            cv.put("telefono", farm.optString("telefono", ""));
            cv.put("correo", farm.optString("correo", ""));
            cv.put("foto", farm.optString("foto", ""));
            cv.put("descripcion", farm.optString("descripcion", ""));
            cv.put("chat_habilitado", farm.optBoolean("chat_habilitado", false) ? 1 : 0);
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
                    obj.put("_id", c.getString(c.getColumnIndexOrThrow("id")));
                    obj.put("_rev", c.getString(c.getColumnIndexOrThrow("rev")));
                    obj.put("empresa", c.getString(c.getColumnIndexOrThrow("empresa")));
                    obj.put("direccion", c.getString(c.getColumnIndexOrThrow("direccion")));
                    obj.put("telefono", c.getString(c.getColumnIndexOrThrow("telefono")));
                    obj.put("correo", c.getString(c.getColumnIndexOrThrow("correo")));
                    obj.put("foto", c.getString(c.getColumnIndexOrThrow("foto")));
                    obj.put("descripcion", c.getString(c.getColumnIndexOrThrow("descripcion")));
                    obj.put("chat_habilitado", c.getInt(c.getColumnIndexOrThrow("chat_habilitado")) == 1);
                    lista.add(obj);
                } catch (Exception e) {}
            } while (c.moveToNext());
        }
        c.close();
        return lista;
    }

    public void guardarMedicamentoCache(JSONObject med) {
        guardarMedicamentoCache(med, false);
    }

    public void guardarMedicamentoCache(JSONObject med, boolean forzar) {
        try {
            String id = med.getString("_id");
            
            // ESCUDO: No guardar si está pendiente de sincronizar O si está marcado como borrado localmente
            if (!forzar && (estaPendienteSincronizacion(id) || estaMarcadoComoBorrado(id))) {
                return;
            }

            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("id", id);
            cv.put("rev", med.optString("_rev", ""));
            cv.put("id_farmacia", med.optString("id_farmacia", ""));
            cv.put("nombre", med.optString("nombre", ""));
            cv.put("precio", med.optString("precio", "0"));
            cv.put("stock", med.optString("stock", "0"));
            cv.put("presentacion", med.optString("presentacion", ""));
            cv.put("promocion", med.optBoolean("promocion", false) ? 1 : 0);
            cv.put("foto1", med.optString("foto1", ""));
            cv.put("foto2", med.optString("foto2", ""));
            cv.put("foto3", med.optString("foto3", ""));
            cv.put("nombre_farmacia", med.optString("nombre_farmacia", ""));
            cv.put("enfermedad_objetivo", med.optString("enfermedad_objetivo", "Ninguna"));
            cv.put("is_deleted", 0);
            db.insertWithOnConflict("medicamentos", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e("DBHelper", "Error guardando medicamento cache", e);
        }
    }

    private boolean estaPendienteSincronizacion(String id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM pendientes WHERE url LIKE ?", new String[]{"%" + id + "%"});
        boolean pendiente = c.getCount() > 0;
        c.close();
        return pendiente;
    }

    private boolean estaMarcadoComoBorrado(String id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM medicamentos WHERE id = ? AND is_deleted = 1", new String[]{id});
        boolean borrado = c.getCount() > 0;
        c.close();
        return borrado;
    }

    public void guardarMedicamentoLocal(JSONObject med) {
        guardarMedicamentoCache(med);
    }

    public void eliminarMedicamentoLocal(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        // En lugar de borrar, marcamos como eliminado (Tombstone)
        ContentValues cv = new ContentValues();
        cv.put("is_deleted", 1);
        db.update("medicamentos", cv, "id = ?", new String[]{id});
    }

    public void eliminarMedicamentoDefinitivo(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("medicamentos", "id = ?", new String[]{id});
    }

    public void limpiarMedicamentosFarmacia(String farmaciaId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("medicamentos", "id_farmacia = ? AND is_deleted = 0", new String[]{farmaciaId});
    }

    public void limpiarFarmaciasCache() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("farmacias", null, null);
    }

    public void limpiarMedicamentosPromocion() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("medicamentos", "promocion = 1 AND is_deleted = 0", null);
    }

    public void limpiarDatosHuerfanos() {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.execSQL("DELETE FROM medicamentos WHERE id_farmacia NOT IN (SELECT id FROM farmacias) AND is_deleted = 0");
            db.execSQL("DELETE FROM medicamentos WHERE nombre IS NULL OR nombre = ''");
        } catch (Exception e) {}
    }

    public List<JSONObject> obtenerMedicamentosCache(String query, boolean soloPromos) {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        
        // FILTRO: is_deleted = 0
        String sql = "SELECT m.* FROM medicamentos m WHERE m.is_deleted = 0 AND m.id_farmacia IN (SELECT id FROM farmacias)";
        
        List<String> selectionArgs = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();

        if (soloPromos) {
            whereClause.append(" AND m.promocion = 1");
        } else if (query != null && !query.isEmpty()) {
            String cleanQuery = query.toLowerCase().replaceAll("[.,]", "").trim();
            String normalizedQuery = cleanQuery.replaceAll("[áàä]", "a").replaceAll("[éèë]", "e").replaceAll("[íìï]", "i").replaceAll("[óòö]", "o").replaceAll("[úùü]", "u");
            String param = "%" + normalizedQuery + "%";
            String sqlNormalizer = "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(%s), 'á','a'), 'é','e'), 'í','i'), 'ó','o'), 'ú','u'), 'ü','u')";
            whereClause.append(" AND (" + String.format(sqlNormalizer, "m.enfermedad_objetivo") + " LIKE ? OR " + String.format(sqlNormalizer, "m.nombre") + " LIKE ? OR " + String.format(sqlNormalizer, "m.presentacion") + " LIKE ?)");
            selectionArgs.add(param); selectionArgs.add(param); selectionArgs.add(param);
        }
        sql += whereClause.toString();

        Cursor cursor = db.rawQuery(sql, selectionArgs.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("_id", cursor.getString(cursor.getColumnIndexOrThrow("id")));
                    obj.put("_rev", cursor.getString(cursor.getColumnIndexOrThrow("rev")));
                    obj.put("id_farmacia", cursor.getString(cursor.getColumnIndexOrThrow("id_farmacia")));
                    obj.put("nombre", cursor.getString(cursor.getColumnIndexOrThrow("nombre")));
                    obj.put("precio", cursor.getString(cursor.getColumnIndexOrThrow("precio")));
                    obj.put("stock", cursor.getString(cursor.getColumnIndexOrThrow("stock")));
                    obj.put("presentacion", cursor.getString(cursor.getColumnIndexOrThrow("presentacion")));
                    obj.put("promocion", cursor.getInt(cursor.getColumnIndexOrThrow("promocion")) == 1);
                    obj.put("foto1", cursor.getString(cursor.getColumnIndexOrThrow("foto1")));
                    obj.put("foto2", cursor.getString(cursor.getColumnIndexOrThrow("foto2")));
                    obj.put("foto3", cursor.getString(cursor.getColumnIndexOrThrow("foto3")));
                    obj.put("nombre_farmacia", cursor.getString(cursor.getColumnIndexOrThrow("nombre_farmacia")));
                    obj.put("enfermedad_objetivo", cursor.getString(cursor.getColumnIndexOrThrow("enfermedad_objetivo")));
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
            cv.put("rev", p.optString("_rev", ""));
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

    public void eliminarPedidoLocal(String id) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete("pedidos", "id = ?", new String[]{id});
        } catch (Exception e) {}
    }

    public void actualizarRevEnPendientes(String id, String nuevoRev) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            Cursor c = db.rawQuery("SELECT id, url, json FROM pendientes WHERE url LIKE ?", new String[]{"%" + id + "%"});
            if (c.moveToFirst()) {
                do {
                    int pId = c.getInt(0);
                    String pUrl = c.getString(1);
                    String pJson = c.getString(2);
                    if (pUrl.contains("?rev=")) pUrl = pUrl.substring(0, pUrl.indexOf("?rev=")) + "?rev=" + nuevoRev;
                    else if (pUrl.contains("&rev=")) pUrl = pUrl.replaceAll("rev=[^&]+", "rev=" + nuevoRev);
                    try {
                        if (pJson != null && pJson.startsWith("{")) {
                            JSONObject jobj = new JSONObject(pJson);
                            if (jobj.has("_rev")) { jobj.put("_rev", nuevoRev); pJson = jobj.toString(); }
                        }
                    } catch (Exception e) {}
                    ContentValues cv = new ContentValues();
                    cv.put("url", pUrl); cv.put("json", pJson);
                    db.update("pendientes", cv, "id = ?", new String[]{String.valueOf(pId)});
                } while (c.moveToNext());
            }
            c.close();
        } catch (Exception e) {}
    }

    public void limpiarPedidosUsuario(String correo) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete("pedidos", "cliente_correo = ?", new String[]{correo});
        } catch (Exception e) {}
    }

    public void limpiarPedidosFarmacia(String farmaciaId) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete("pedidos", "farmacias_ids LIKE ?", new String[]{"%\"" + farmaciaId + "\"%"});
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
                    obj.put("_id", c.getString(c.getColumnIndexOrThrow("id")));
                    obj.put("_rev", c.getString(c.getColumnIndexOrThrow("rev")));
                    obj.put("cliente_correo", c.getString(c.getColumnIndexOrThrow("cliente_correo")));
                    obj.put("cliente_nombre", c.getString(c.getColumnIndexOrThrow("cliente_nombre")));
                    obj.put("cliente_direccion", c.getString(c.getColumnIndexOrThrow("cliente_direccion")));
                    obj.put("cliente_telefono", c.getString(c.getColumnIndexOrThrow("cliente_telefono")));
                    obj.put("items", new JSONArray(c.getString(c.getColumnIndexOrThrow("items"))));
                    obj.put("total", c.getString(c.getColumnIndexOrThrow("total")));
                    obj.put("fecha", c.getString(c.getColumnIndexOrThrow("fecha")));
                    obj.put("estado", c.getString(c.getColumnIndexOrThrow("estado")));
                    obj.put("metodo_pago", c.getString(c.getColumnIndexOrThrow("metodo_pago")));
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
                    obj.put("_id", c.getString(c.getColumnIndexOrThrow("id")));
                    obj.put("_rev", c.getString(c.getColumnIndexOrThrow("rev")));
                    obj.put("cliente_correo", c.getString(c.getColumnIndexOrThrow("cliente_correo")));
                    obj.put("cliente_nombre", c.getString(c.getColumnIndexOrThrow("cliente_nombre")));
                    obj.put("cliente_direccion", c.getString(c.getColumnIndexOrThrow("cliente_direccion")));
                    obj.put("cliente_telefono", c.getString(c.getColumnIndexOrThrow("cliente_telefono")));
                    obj.put("items", new JSONArray(c.getString(c.getColumnIndexOrThrow("items"))));
                    obj.put("total", c.getString(c.getColumnIndexOrThrow("total")));
                    obj.put("fecha", c.getString(c.getColumnIndexOrThrow("fecha")));
                    obj.put("estado", c.getString(c.getColumnIndexOrThrow("estado")));
                    obj.put("metodo_pago", c.getString(c.getColumnIndexOrThrow("metodo_pago")));
                    obj.put("farmacias_ids", new JSONArray(c.getString(c.getColumnIndexOrThrow("farmacias_ids"))));
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
            db.execSQL("INSERT OR REPLACE INTO usuarios(id, rev, nombres, apellidos, telefono, correo, clave, direccion, alergias, tipo_sangre, enfermedades, foto) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", datos);
        } else if (accion.equals("modificar")) {
            db.execSQL("UPDATE usuarios SET rev=?, nombres=?, apellidos=?, telefono=?, correo=?, clave=?, direccion=?, alergias=?, tipo_sangre=?, enfermedades=?, foto=? WHERE id=?", 
                new String[]{datos[1], datos[2], datos[3], datos[4], datos[5], datos[6], datos[7], datos[8], datos[9], datos[10], datos[11], datos[0]});
        }
    }

    public JSONObject obtenerFarmaciaLocal(String id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM farmacias WHERE id=?", new String[]{id});
        if (c.moveToFirst()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("_id", c.getString(c.getColumnIndexOrThrow("id")));
                obj.put("_rev", c.getString(c.getColumnIndexOrThrow("rev")));
                obj.put("empresa", c.getString(c.getColumnIndexOrThrow("empresa")));
                obj.put("direccion", c.getString(c.getColumnIndexOrThrow("direccion")));
                obj.put("telefono", c.getString(c.getColumnIndexOrThrow("telefono")));
                obj.put("correo", c.getString(c.getColumnIndexOrThrow("correo")));
                obj.put("clave", c.getString(c.getColumnIndexOrThrow("clave")));
                obj.put("foto", c.getString(c.getColumnIndexOrThrow("foto")));
                obj.put("descripcion", c.getString(c.getColumnIndexOrThrow("descripcion")));
                obj.put("chat_habilitado", c.getInt(c.getColumnIndexOrThrow("chat_habilitado")) == 1);
                obj.put("tipo", "farmacia");
                c.close();
                return obj;
            } catch (Exception e) {}
        }
        c.close();
        return null;
    }

    public void administrarFarmacias(String accion, String[] datos) {
        SQLiteDatabase db = getWritableDatabase();
        if (accion.equals("nuevo")) {
            db.execSQL("INSERT OR REPLACE INTO farmacias(id, rev, empresa, direccion, telefono, correo, clave, foto, descripcion, chat_habilitado) VALUES(?,?,?,?,?,?,?,?,?,?)", datos);
        } else if (accion.equals("modificar")) {
            db.execSQL("UPDATE farmacias SET rev=?, empresa=?, direccion=?, telefono=?, correo=?, clave=?, foto=?, descripcion=?, chat_habilitado=? WHERE id=?", 
                new String[]{datos[1], datos[2], datos[3], datos[4], datos[5], datos[6], datos[7], datos[8], datos[9], datos[0]});
        }
    }

    public Cursor login(String correo, String clave) {
        return getReadableDatabase().rawQuery("SELECT * FROM usuarios WHERE correo=? AND clave=?", new String[]{correo, clave});
    }

    public void guardarMensajeLocal(JSONObject m) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            String id = m.getString("_id");
            int leido = m.optInt("leido", 0);

            // ESCUDO: Si el servidor dice que no está leído (0), pero localmente ya lo marcamos como leído (1),
            // mantenemos el estado local para evitar que la "burbuja" reaparezca por retraso del servidor.
            if (leido == 0) {
                Cursor c = db.rawQuery("SELECT leido FROM mensajes WHERE id=?", new String[]{id});
                if (c.moveToFirst()) {
                    if (c.getInt(0) == 1) leido = 1;
                }
                c.close();
            }

            ContentValues cv = new ContentValues();
            cv.put("id", id);
            cv.put("rev", m.optString("_rev", ""));
            cv.put("emisor", m.getString("emisor"));
            cv.put("receptor", m.getString("receptor"));
            cv.put("mensaje", m.getString("mensaje"));
            cv.put("fecha", m.getString("fecha"));
            cv.put("id_farmacia", m.getString("id_farmacia"));
            cv.put("id_usuario", m.getString("id_usuario"));
            cv.put("leido", leido);
            cv.put("emisor_nombre", m.optString("emisor_nombre", "Desconocido"));
            cv.put("cliente_nombre_completo", m.optString("cliente_nombre_completo", "Cliente"));
            db.insertWithOnConflict("mensajes", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e("DBHelper", "Error guardando mensaje", e);
        }
    }

    public List<JSONObject> obtenerMensajesChat(String farmaciaId, String usuarioId) {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM mensajes WHERE id_farmacia=? AND id_usuario=? ORDER BY fecha ASC", new String[]{farmaciaId, usuarioId});
        if (c.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("_id", c.getString(c.getColumnIndexOrThrow("id")));
                    obj.put("emisor", c.getString(c.getColumnIndexOrThrow("emisor")));
                    obj.put("receptor", c.getString(c.getColumnIndexOrThrow("receptor")));
                    obj.put("mensaje", c.getString(c.getColumnIndexOrThrow("mensaje")));
                    obj.put("fecha", c.getString(c.getColumnIndexOrThrow("fecha")));
                    obj.put("id_farmacia", c.getString(c.getColumnIndexOrThrow("id_farmacia")));
                    obj.put("id_usuario", c.getString(c.getColumnIndexOrThrow("id_usuario")));
                    obj.put("leido", c.getInt(c.getColumnIndexOrThrow("leido")));
                    obj.put("emisor_nombre", c.getString(c.getColumnIndexOrThrow("emisor_nombre")));
                    lista.add(obj);
                } catch (Exception e) {}
            } while (c.moveToNext());
        }
        c.close();
        return lista;
    }

    public List<JSONObject> obtenerConversacionesFarmacia(String farmaciaId) {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        
        // Agrupamos por id_usuario para tener una fila por chat, mostrando el último mensaje
        // Además contamos cuántos mensajes NO leídos hay para ese usuario y farmacia
        String sql = "SELECT m.*, (SELECT COUNT(*) FROM mensajes WHERE id_farmacia=m.id_farmacia AND id_usuario=m.id_usuario AND leido=0 AND emisor != m.id_farmacia) as no_leidos " +
                     "FROM mensajes m INNER JOIN (SELECT id_usuario, MAX(fecha) as max_fecha FROM mensajes WHERE id_farmacia=? GROUP BY id_usuario) t " +
                     "ON m.id_usuario = t.id_usuario AND m.fecha = t.max_fecha WHERE m.id_farmacia=? ORDER BY m.fecha DESC";
        
        Cursor c = db.rawQuery(sql, new String[]{farmaciaId, farmaciaId});
        if (c.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("id_usuario", c.getString(c.getColumnIndexOrThrow("id_usuario")));
                    obj.put("ultimo_mensaje", c.getString(c.getColumnIndexOrThrow("mensaje")));
                    obj.put("fecha", c.getString(c.getColumnIndexOrThrow("fecha")));
                    obj.put("nombre_cliente", c.getString(c.getColumnIndexOrThrow("cliente_nombre_completo")));
                    obj.put("no_leidos", c.getInt(c.getColumnIndexOrThrow("no_leidos")));
                    lista.add(obj);
                } catch (Exception e) {}
            } while (c.moveToNext());
        }
        c.close();
        return lista;
    }

    public void marcarComoLeido(String farmaciaId, String usuarioId, String idPropio) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put("leido", 1);
            // Marcamos como leído todo lo que NO hayamos enviado nosotros (el emisor no es idPropio)
            db.update("mensajes", cv, "id_farmacia=? AND id_usuario=? AND emisor != ?", new String[]{farmaciaId, usuarioId, idPropio});
        } catch (Exception e) {}
    }

    public Cursor loginFarmacia(String correo, String clave) {
        return getReadableDatabase().rawQuery("SELECT * FROM farmacias WHERE LOWER(correo)=LOWER(?) AND clave=?", new String[]{correo, clave});
    }
}
