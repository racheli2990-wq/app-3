package com.racheli.offlinenotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class NotesDb extends SQLiteOpenHelper {
    NotesDb(Context context) { super(context, "offline_notes.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) { createNotes(db); createLifeVaultTables(db); }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2) createLifeVaultTables(db);
    }
    private void createNotes(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL, image_path TEXT, audio_path TEXT, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS notes_updated_at_idx ON notes(updated_at DESC)");
    }
    private void createLifeVaultTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', due_date TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS tasks_updated_at_idx ON tasks(updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS vault_items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, mime_type TEXT NOT NULL, path TEXT NOT NULL, size INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS vault_created_at_idx ON vault_items(created_at DESC)");
    }
    List<Note> all() {
        List<Note> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("notes",null,null,null,null,null,"updated_at DESC")) {
            while(c.moveToNext()) result.add(readNote(c));
        }
        return result;
    }
    Note get(long id) {
        try(Cursor c=getReadableDatabase().query("notes",null,"id=?",new String[]{String.valueOf(id)},null,null,null)) {
            return c.moveToFirst()?readNote(c):null;
        }
    }
    long save(Note n) {
        ContentValues v=new ContentValues();
        v.put("title",n.title); v.put("body",n.body); v.put("image_path",n.imagePath);
        v.put("audio_path",n.audioPath); v.put("updated_at",System.currentTimeMillis());
        if(n.id>0){getWritableDatabase().update("notes",v,"id=?",new String[]{String.valueOf(n.id)});return n.id;}
        return getWritableDatabase().insertOrThrow("notes",null,v);
    }
    void delete(long id){getWritableDatabase().delete("notes","id=?",new String[]{String.valueOf(id)});}
    private Note readNote(Cursor c) {
        return new Note(c.getLong(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("title")),
            c.getString(c.getColumnIndexOrThrow("body")),c.getString(c.getColumnIndexOrThrow("image_path")),
            c.getString(c.getColumnIndexOrThrow("audio_path")),c.getLong(c.getColumnIndexOrThrow("updated_at")));
    }
    List<TaskItem> allTasks() {
        List<TaskItem> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("tasks",null,null,null,null,null,"done ASC, updated_at DESC")) {
            while(c.moveToNext()) result.add(readTask(c));
        }
        return result;
    }
    long saveTask(TaskItem t) {
        ContentValues v=new ContentValues();v.put("title",t.title);v.put("detail",t.detail);
        v.put("due_date",t.dueDate);v.put("done",t.done?1:0);v.put("updated_at",System.currentTimeMillis());
        if(t.id>0){getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(t.id)});return t.id;}
        return getWritableDatabase().insertOrThrow("tasks",null,v);
    }
    void setTaskDone(long id,boolean done) {
        ContentValues v=new ContentValues();v.put("done",done?1:0);v.put("updated_at",System.currentTimeMillis());
        getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});
    }
    void deleteTask(long id){getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)});}
    private TaskItem readTask(Cursor c) {
        return new TaskItem(c.getLong(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("title")),
            c.getString(c.getColumnIndexOrThrow("detail")),c.getString(c.getColumnIndexOrThrow("due_date")),
            c.getInt(c.getColumnIndexOrThrow("done"))!=0,c.getLong(c.getColumnIndexOrThrow("updated_at")));
    }
    List<VaultItem> allVaultItems() {
        List<VaultItem> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("vault_items",null,null,null,null,null,"created_at DESC")) {
            while(c.moveToNext()) result.add(readVaultItem(c));
        }
        return result;
    }
    long addVaultItem(String name,String mime,String path,long size) {
        ContentValues v=new ContentValues();v.put("name",name);v.put("mime_type",mime);
        v.put("path",path);v.put("size",size);v.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("vault_items",null,v);
    }
    void deleteVaultItem(long id){getWritableDatabase().delete("vault_items","id=?",new String[]{String.valueOf(id)});}
    private VaultItem readVaultItem(Cursor c) {
        return new VaultItem(c.getLong(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("name")),
            c.getString(c.getColumnIndexOrThrow("mime_type")),c.getString(c.getColumnIndexOrThrow("path")),
            c.getLong(c.getColumnIndexOrThrow("size")),c.getLong(c.getColumnIndexOrThrow("created_at")));
    }
}
