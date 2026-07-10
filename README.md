# Rename Utility

This is a simple Android tool designed to rename file extensions in bulk. The main goal is to help users, primarily photographers, to quickly change their file extensions without needing to use a computer.

### Key Features

* Bulk extension renaming.
* Background processing using Android services, allowing you to use other apps while the task runs.
* Validation to prevent renaming errors.
* Option to add custom prefixes and sequential numbering to files.
* Intuitive interface based on Material 3.
* Error logging to identify files that could not be processed.

### How to Use

1. Upon opening the app, select the folder where the files you want to rename are located.
2. Enter the current file extension (e.g., .CR3).
3. Enter the new extension you want to apply (e.g., .CR2).
4. Optionally, define a prefix if you want your files to have a specific batch name.
5. Press the rename button and wait for the process to finish. Progress will be displayed in a notification and within the app screen.

### Required Permissions

The application requires file access permission to read and write within the folder you select. This access is managed through the Android Storage Access Framework and only affects the folder chosen by the user.

### Technical Note

The application uses a Foreground Service to ensure the renaming process is not interrupted by the operating system, guaranteeing that all files are processed correctly even if the application is minimized.
