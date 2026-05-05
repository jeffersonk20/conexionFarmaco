package com.example.conexionfarmaco;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.Nullable;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "conexion_farmaco.db";
    private static final int DATABASE_VERSION = 1;

    public DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE usuarios (id TEXT PRIMARY KEY, nombres TEXT, apellidos TEXT, telefono TEXT, correo TEXT, clave TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS usuarios");
        onCreate(db);
    }

    public String administrarUsuarios(String accion, String[] datos) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (accion.equals("nuevo")) {
                db.execSQL("INSERT INTO usuarios(id, nombres, apellidos, telefono, correo, clave) VALUES(?,?,?,?,?,?)", datos);
            } else if (accion.equals("modificar")) {
                db.execSQL("UPDATE usuarios SET nombres=?, apellidos=?, telefono=?, correo=?, clave=? WHERE id=?", 
                    new String[]{datos[1], datos[2], datos[3], datos[4], datos[5], datos[0]});
            } else if (accion.equals("eliminar")) {
                db.execSQL("DELETE FROM usuarios WHERE id=?", new String[]{datos[0]});
            }
            return "ok";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public Cursor login(String correo, String clave) {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery("SELECT * FROM usuarios WHERE correo=? AND clave=?", new String[]{correo, clave});
    }

    public boolean buscarCorreo(String correo) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM usuarios WHERE correo=?", new String[]{correo});
        boolean existe = cursor.getCount() > 0;
        cursor.close();
        return existe;
    }
}
