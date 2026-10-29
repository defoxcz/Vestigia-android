package com.vestigiascriptorium.android.genealogy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Test APK only. Exposes fixed synthetic bytes, with no access to application/user files. */
public final class PhotoFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return uri.getPath().endsWith(".ged") ? "application/octet-stream" : "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        cursor.addRow(new Object[]{uri.getLastPathSegment(),null}); return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.equals("r")) throw new FileNotFoundException();
        final byte[] bytes;
        if (uri.getPath().equals("/family.ged")) {
            bytes=("0 HEAD\n1 GEDC\n2 VERS 7.0\n0 @I1@ INDI\n1 NAME Anna /Nováková/\n1 BIRT\n2 DATE 1820\n0 TRLR\n").getBytes(StandardCharsets.UTF_8);
        } else if (uri.getPath().equals("/portrait.png")) {
            Bitmap image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);image.eraseColor(Color.rgb(30,120,80));
            ByteArrayOutputStream output=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,output);image.recycle();bytes=output.toByteArray();
        } else throw new FileNotFoundException();
        try {
            final ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            new Thread(() -> {try (OutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {output.write(bytes);} catch(IOException ignored) {}}).start();
            return pipe[0];
        } catch(IOException e) { throw new FileNotFoundException(); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) {throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri, String selection, String[] args) {throw new UnsupportedOperationException();}
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {throw new UnsupportedOperationException();}
}
