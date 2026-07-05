package com.niftyfiftysoftware.renameutility.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.DocumentsContract;

import com.niftyfiftysoftware.renameutility.interfaces.RenameCallback;

import java.util.ArrayList;
import java.util.HashSet;

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

    public static int renameFilesFast(Context context, Uri treeUri, String query, String newExtension, String prefix, RenameCallback callback, Handler handler) {
        if (Thread.currentThread().isInterrupted()) return 0;
        ContentResolver resolver = context.getContentResolver();
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);

        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        HashSet<String> existingNames = new HashSet<>();
        ArrayList<String> failedFiles = new ArrayList<>();
        int totalTargets = 0;

        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    String mimeType = cursor.getString(2);
                    if (name != null) {
                        existingNames.add(name.toLowerCase());
                        if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType) && name.toLowerCase().contains(query.toLowerCase())) {
                            totalTargets++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (totalTargets == 0) return 0;

        int renamedCount = 0;
        int globalCounter = 1;

        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        if (callback != null && handler != null) {
                            handler.post(callback::onRenameCancelled);
                        }
                        return renamedCount;
                    }
                    String currentDocId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mimeType = cursor.getString(2);

                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        if (name != null && name.toLowerCase().contains(query.toLowerCase())) {
                            String baseName;
                            if (prefix != null && !prefix.trim().isEmpty()) {
                                baseName = prefix.trim() + "_" + String.format("%02d", globalCounter);
                            } else {
                                int lastDotIndex = name.lastIndexOf(".");
                                baseName = lastDotIndex != -1 ? name.substring(0, lastDotIndex) : name;
                            }

                            String finalExt = newExtension.startsWith(".") ? newExtension : "." + newExtension;
                            String newName = baseName + finalExt;

                            int colCounter = 1;
                            while (existingNames.contains(newName.toLowerCase()) && !newName.equalsIgnoreCase(name)) {
                                newName = baseName + "(" + colCounter + ")" + finalExt;
                                colCounter++;
                            }

                            try {
                                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId);
                                DocumentsContract.renameDocument(resolver, documentUri, newName);
                                existingNames.add(newName.toLowerCase());
                                renamedCount++;
                                globalCounter++;
                            } catch (Exception e) {
                                failedFiles.add(name);
                                globalCounter++;
                            }

                            if (callback != null && handler != null) {
                                final int current = renamedCount + failedFiles.size();
                                final int total = totalTargets;
                                handler.post(() -> callback.onRenameProgress(current, total));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (callback != null && handler != null && !Thread.currentThread().isInterrupted()) {
            final int finalCount = renamedCount;
            handler.post(() -> callback.onRenameComplete(finalCount, failedFiles));
        }

        return renamedCount;
    }
}