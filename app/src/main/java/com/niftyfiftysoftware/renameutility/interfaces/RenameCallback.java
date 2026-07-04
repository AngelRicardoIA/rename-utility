package com.niftyfiftysoftware.renameutility.interfaces;

public interface RenameCallback {
    void onRenameProgress(int current, int total);
    void onRenameComplete(int totalRenamed);
    void onRenameCancelled();
    void onRenameError(String message);
}