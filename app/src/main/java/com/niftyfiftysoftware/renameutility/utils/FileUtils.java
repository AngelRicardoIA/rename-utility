package com.niftyfiftysoftware.renameutility.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

public class FileUtils {

    public static int startFastSearch(Context context, Uri treeUri, String query) {
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        return countFilesFast(context, treeUri, docId, query.toLowerCase());
    }

    private static int countFilesFast(Context context, Uri treeUri, String documentId, String query) {
        if (Thread.currentThread().isInterrupted()) return 0;
        int count = 0;
        ContentResolver resolver = context.getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (Thread.currentThread().isInterrupted()) return count;
                    String docId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mimeType = cursor.getString(2);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        count += countFilesFast(context, treeUri, docId, query);
                    } else {
                        if (name != null && name.toLowerCase().contains(query)) {
                            count++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    public static int renameFilesFast(Context context, Uri treeUri, String query, String newExtension) {
        if (Thread.currentThread().isInterrupted()) return 0;
        int renamedCount = 0;
        ContentResolver resolver = context.getContentResolver();
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (Thread.currentThread().isInterrupted()) break;
                    String currentDocId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mimeType = cursor.getString(2);
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        if (name != null && name.toLowerCase().contains(query.toLowerCase())) {
                            int lastDotIndex = name.lastIndexOf(".");
                            String newName;
                            if (lastDotIndex != -1) {
                                newName = name.substring(0, lastDotIndex) + (newExtension.startsWith(".") ? newExtension : "." + newExtension);
                            } else {
                                newName = name + (newExtension.startsWith(".") ? newExtension : "." + newExtension);
                            }
                            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId);
                            DocumentsContract.renameDocument(resolver, documentUri, newName);
                            renamedCount++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return renamedCount;
    }
}