package dev.dexdroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Temporary app-choice destination, behind menus and with no input ownership. */
final class DestinationHighlight extends View {
    private final SessionService session;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DestinationHighlight(Context context) {
        this(context, null);
    }

    DestinationHighlight(Context context, SessionService session) {
        super(context);
        this.session = session;
        setTag("app-destination-highlight");
        setForceDarkAllowed(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        SplitLayout.Box box = session == null ? null : session.appSelectionBox();
        if (box == null || box.width <= 0 || box.height <= 0) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(14, 255, 255, 255));
        canvas.drawRect(box.x, box.y, box.x + box.width, box.y + box.height, paint);
        float stroke = Math.max(1, session.dpi / 160f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(Color.rgb(96, 96, 96));
        canvas.drawRect(
                box.x + stroke / 2,
                box.y + stroke / 2,
                box.x + box.width - stroke / 2,
                box.y + box.height - stroke / 2,
                paint);
    }
}
