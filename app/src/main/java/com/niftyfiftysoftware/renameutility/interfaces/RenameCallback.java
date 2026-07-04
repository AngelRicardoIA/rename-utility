package com.niftyfiftysoftware.renameutility.interfaces;

public interface RenameCallback {
    void onRenameComplete(int totalRenamed);
    void onRenameCancelled();
    void onRenameError(String message);
}