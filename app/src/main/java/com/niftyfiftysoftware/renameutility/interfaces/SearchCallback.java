package com.niftyfiftysoftware.renameutility.interfaces;

public interface SearchCallback {
    void onSearchComplete(int totalFiles);
    void onSearchCancelled();
}