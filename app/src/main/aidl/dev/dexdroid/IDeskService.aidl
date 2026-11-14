package dev.dexdroid;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.KeyEvent;
import dev.dexdroid.IDesktopListener;
import android.os.Bundle;
import dev.dexdroid.IKeyboardPolicyListener;
interface IDeskService {
 int create(in Surface surface, int width, int height, int dpi, boolean nativeKeyboardFocus, IBinder owner, IDesktopListener listener);
 void resize(in Surface surface, int width, int height, int dpi);
 void touch(int display, int action, in float[] xs, in float[] ys);
 void relayTouch(int display, in MotionEvent event);
 void relayMotion(int display, in MotionEvent event);
 void relayKey(int display, in KeyEvent event);
 void closeApps(int display);
 void scroll(int display, float x, float y, float horizontal, float vertical);
 String launch(int display, String component);
 void key(int display, int code);
 void stop();
 boolean focusController(int taskId);
 Bundle audioBegin(long epoch, IBinder owner);
 Bundle audioQuery(long epoch);
 Bundle audioSelect(long epoch, String key);
 Bundle audioEnd(long epoch);
 Bundle disableDebugging();
 Bundle workspaceTask(int display);
 void keyboardPolicy(int display, boolean enabled, long request, IKeyboardPolicyListener listener);
 Bundle phoneScreen(int command, int display, long lease, long deadline, IBinder owner, String expectedBoot, long expectedPhysical);
}
