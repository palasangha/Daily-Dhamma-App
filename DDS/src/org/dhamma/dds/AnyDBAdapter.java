package org.dhamma.dds;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class AnyDBAdapter {

    private static final String TAG = "AnyDBAdapter";
    private DatabaseHelper mDbHelper;
    private static SQLiteDatabase mDb;

    //make sure this matches the 
    //package com.MyPackage;
    //at the top of this file
    private static String DB_VERSION = "";
    private static String DB_PATH = "/sdcard/tflash/dds/";//databases/

    //make sure this matches your database name in your assets folder
    // my database file does not have an extension on it 
    // if yours does
    // add the extention
    private static final String DATABASE_NAME = "data.db";

    //Im using an sqlite3 database, I have no clue if this makes a difference or not
    private static final int DATABASE_VERSION = 1;

    private final Context adapterContext;

    public AnyDBAdapter(Context context, String path) {
        this.adapterContext = context;
        DB_PATH = path;
    }

    public AnyDBAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(adapterContext);

        try {
            mDbHelper.createDataBase();
        } catch (IOException ioe) {
            throw new Error("Unable to create database");
        }

        try {
            mDbHelper.openDataBase();
        } catch (SQLException sqle) {
            throw sqle;
        }
        return this;
    }
    //Usage from outside
    // AnyDBAdapter dba = new AnyDBAdapter(contextObject); //in my case contextObject is a Map
    // dba.open();
    // Cursor c = dba.ExampleSelect("Rawr!");
    // contextObject.startManagingCursor(c);
    // String s1 = "", s2 = "";
    // if(c.moveToFirst())
    // do {
    //  s1 = c.getString(0);
    //  s2 = c.getString(1);
    //  } while (c.moveToNext());
    // dba.close();
    public Cursor MySelect(String qry)
    {
        //String query = "SELECT locale, ? FROM android_metadata";
       // return mDb.rawQuery(qry, new String[]{myVariable});
    	return mDb.rawQuery(qry, null);
    }

    //Usage
    // AnyDBAdatper dba = new AnyDBAdapter(contextObjecT);
    // dba.open();
    // dba.ExampleCommand("en-CA", "en-GB");
    // dba.close();
    public void MyCommand(String qry)//String myVariable1, String myVariable2)
    {
        //String command = "INSERT INTO android_metadata (locale) SELECT ? UNION ALL SELECT ?";
        //mDb.execSQL(command, new String[]{ myVariable1, myVariable2});
    	mDb.execSQL(qry, new String[]{});
    }

    public void close() {
    	mDb.close();
        mDbHelper.close();
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        Context helperContext;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            helperContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database!!!!!");
            //db.execSQL("");
            onCreate(db);
        }

        public void createDataBase() throws IOException {
            boolean dbExist = checkDataBase();
            if (dbExist) {
            } else {

                //make sure your database has this table already created in it
                //this does not actually work here
                /*
                 * db.execSQL("CREATE TABLE IF NOT EXISTS \"android_metadata\" (\"locale\" TEXT DEFAULT 'en_US')"
                 * );
                 * db.execSQL("INSERT INTO \"android_metadata\" VALUES ('en_US')"
                 * );
                 */
                this.getReadableDatabase();
                try {
                    copyDataBase();
                } catch (IOException e) {
                    throw new Error("Error copying database");
                }
            }
        }

        public SQLiteDatabase getDatabase() {
            String myPath = DB_PATH + DATABASE_NAME;// + DB_VERSION;
            return SQLiteDatabase.openDatabase(myPath, null,
                    SQLiteDatabase.OPEN_READONLY);
        }

        private boolean checkDataBase() {
            SQLiteDatabase checkDB = null;
            try {
                String myPath = DB_PATH + DATABASE_NAME;// + DB_VERSION;
                checkDB = SQLiteDatabase.openDatabase(myPath, null,
                        SQLiteDatabase.OPEN_READONLY);
            } catch (SQLiteException e) {
            }
            if (checkDB != null) {
                checkDB.close();
            }
            return checkDB != null ? true : false;
        }

        private void copyDataBase() throws IOException {

            // Open your local db as the input stream
            InputStream myInput = helperContext.getAssets().open(DATABASE_NAME);

            // Path to the just created empty db
            String outFileName = DB_PATH + DATABASE_NAME;//  + DB_VERSION;
            File folder = new File(DB_PATH);
            if(!folder.exists()){
                folder.mkdirs();
            }            
            // Open the empty db as the output stream
            OutputStream myOutput = new FileOutputStream(outFileName);

            // transfer bytes from the inputfile to the outputfile
            byte[] buffer = new byte[1024];
            int length;
            while ((length = myInput.read(buffer)) > 0) {
                myOutput.write(buffer, 0, length);
            }

            // Close the streams
            myOutput.flush();
            myOutput.close();
            myInput.close();
        }

        public void openDataBase() throws SQLException {
            // Open the database
            String myPath = DB_PATH + DATABASE_NAME; //  + DB_VERSION;
            mDb = SQLiteDatabase.openDatabase(myPath, null,
                    SQLiteDatabase.OPEN_READWRITE);
        }

        @Override
        public synchronized void close() {

            if (mDb != null)
                mDb.close();

            super.close();

        }
    }

}