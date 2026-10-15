package dev.dexdroid;
oneway interface IDesktopListener {
 void onProtectionState(int display, int state);
 void onTaskState(int display, boolean occupied, String error);
}
