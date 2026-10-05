package kr.family.praisesticker;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public class ShareProvider extends ContentProvider {
  public boolean onCreate() {
    return true;
  }

  File file(Uri u) throws FileNotFoundException {
    if (!"/praise-sticker.png".equals(u.getPath())) throw new FileNotFoundException();
    return new File(getContext().getCacheDir(), "share/praise-sticker.png");
  }

  public String getType(Uri u) {
    return "image/png";
  }

  public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException();
    return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  public Cursor query(Uri u, String[] projection, String selection, String[] args, String order) {
    try {
      File f = file(u);
      String[] cols =
          projection == null
              ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
              : projection;
      MatrixCursor cursor = new MatrixCursor(cols);
      Object[] row = new Object[cols.length];
      for (int i = 0; i < cols.length; i++)
        row[i] =
            cols[i].equals(OpenableColumns.DISPLAY_NAME)
                ? "칭찬스티커.png"
                : cols[i].equals(OpenableColumns.SIZE) ? f.length() : null;
      cursor.addRow(row);
      return cursor;
    } catch (Exception e) {
      return null;
    }
  }

  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
