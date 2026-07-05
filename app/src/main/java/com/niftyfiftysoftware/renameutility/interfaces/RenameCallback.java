package com.niftyfiftysoftware.renameutility.interfaces;

import java.util.ArrayList;

public interface RenameCallback {
    void onRenameProgress(int current, int total);
    void onRenameComplete(int totalRenamed, ArrayList<String> failedFiles);
    void onRenameCancelled();
    void onRenameError(String message);
}