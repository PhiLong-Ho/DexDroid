package dev.dexdroid;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import java.lang.reflect.Method;
import rikka.shizuku.ShizukuApiConstants;

/** Shell-only test input, isolated from product code and absent from release artifacts. */
@android.annotation.SuppressLint("PrivateApi")
public final class TestInputDriver extends Binder {
    private static final String DESCRIPTOR = "dev.dexdroid.TestInputDriver";
    private Context context;
    private Object input;
    private Method inject;

    public TestInputDriver() {}

    private void init() throws Exception {
        if (context != null) return;
        if (android.os.Process.myUid() != 2000)
            throw new SecurityException("Test input requires authorized shell");
        if (Looper.myLooper() == null) Looper.prepare();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        context =
                (Context)
                        thread.getMethod("getSystemContext")
                                .invoke(thread.getMethod("systemMain").invoke(null));
        Class<?> manager;
        try {
            manager = Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException legacy) {
            manager = Class.forName("android.hardware.input.InputManager");
        }
        input = manager.getMethod("getInstance").invoke(null);
        inject = manager.getMethod("injectInputEvent", InputEvent.class, int.class);
    }

    @Override
    protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy) {
            System.exit(0);
            return true;
        }
        if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(DESCRIPTOR);
        int target = data.readInt(), kind = data.readInt();
        long identity = Binder.clearCallingIdentity();
        MotionEvent motion = null;
        try {
            init();
            Display display = context.getSystemService(DisplayManager.class).getDisplay(target);
            if (display == null
                    || target == Display.DEFAULT_DISPLAY
                    || display.getName().contains("DexDroid")
                    || display.getName().contains("PortraitDesk")
                    || (display.getFlags() & Display.FLAG_PRESENTATION) == 0)
                throw new SecurityException(
                        "Test input target must be an external presentation monitor");
            InputEvent event;
            if (kind == 1) {
                motion = MotionEvent.CREATOR.createFromParcel(data);
                if (!motion.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                        || motion.getPointerCount() < 1
                        || motion.getPointerCount() > 16)
                    throw new IllegalArgumentException("Invalid test monitor touch");
                event = motion;
            } else if (kind == 2) {
                event = KeyEvent.CREATOR.createFromParcel(data);
                if (!event.isFromSource(InputDevice.SOURCE_KEYBOARD))
                    throw new IllegalArgumentException("Invalid test keyboard source");
            } else throw new IllegalArgumentException("Invalid test event kind");
            event.getClass().getMethod("setDisplayId", int.class).invoke(event, target);
            boolean accepted = (Boolean) inject.invoke(input, event, 2);
            reply.writeNoException();
            reply.writeInt(accepted ? 1 : 0);
        } catch (Exception error) {
            reply.writeException(new IllegalStateException("Shell test input failed", error));
        } finally {
            if (motion != null) motion.recycle();
            Binder.restoreCallingIdentity(identity);
        }
        return true;
    }
}
