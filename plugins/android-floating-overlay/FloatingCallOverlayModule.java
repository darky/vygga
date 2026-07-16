package expo.modules.floatingcalloverlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class FloatingCallOverlayModule extends ReactContextBaseJavaModule {

  private static final String TAG = "FloatingCallOverlay";
  private static final String MODE_INCOMING = "incoming";
  private static final String MODE_ACTIVE = "active";

  private WindowManager windowManager;
  private View overlayView;
  private WindowManager.LayoutParams params;
  private int initialX;
  private int initialY;
  private float initialTouchX;
  private float initialTouchY;

  FloatingCallOverlayModule(ReactApplicationContext context) {
    super(context);
  }

  @Override
  public String getName() {
    return "FloatingCallOverlay";
  }

  @Override
  public void onCatalystInstanceDestroy() {
    hideOverlay();
    super.onCatalystInstanceDestroy();
  }

  @ReactMethod
  public void hasOverlayPermission(Promise promise) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      promise.resolve(Settings.canDrawOverlays(getReactApplicationContext()));
    } else {
      promise.resolve(true);
    }
  }

  @ReactMethod
  public void requestOverlayPermission(Promise promise) {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "No current activity to launch settings");
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Intent intent = new Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + activity.getPackageName())
      );
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity.startActivity(intent);
    }
    promise.resolve(true);
  }

  @ReactMethod
  public void showIncomingOverlay(String callerAddr, String callId, Promise promise) {
    try {
      if (!checkPermission()) {
        promise.reject("NO_PERMISSION", "SYSTEM_ALERT_WINDOW permission not granted");
        return;
      }
      showOverlay(MODE_INCOMING, callerAddr, callId);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "showIncomingOverlay error", e);
      promise.reject("OVERLAY_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void showActiveOverlay(String remoteAddr, Promise promise) {
    try {
      if (!checkPermission()) {
        promise.reject("NO_PERMISSION", "SYSTEM_ALERT_WINDOW permission not granted");
        return;
      }
      showOverlay(MODE_ACTIVE, remoteAddr, null);
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "showActiveOverlay error", e);
      promise.reject("OVERLAY_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void hideOverlay(Promise promise) {
    try {
      hideOverlay();
      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "hideOverlay error", e);
      promise.reject("OVERLAY_ERROR", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void addListener(String eventName) {}

  @ReactMethod
  public void removeListeners(int count) {}

  private boolean checkPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return Settings.canDrawOverlays(getReactApplicationContext());
    }
    return true;
  }

  private void showOverlay(String mode, String addr, String callId) {
    hideOverlay();

    Context ctx = getReactApplicationContext();
    windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);

    int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
      ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      : WindowManager.LayoutParams.TYPE_PHONE;

    params = new WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      layoutFlag,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    );
    params.gravity = Gravity.TOP | Gravity.START;
    params.x = dpToPx(16);
    params.y = dpToPx(80);

    overlayView = createOverlayView(mode, addr, callId);
    windowManager.addView(overlayView, params);
  }

  private View createOverlayView(String mode, String addr, String callId) {
    Context ctx = getReactApplicationContext();

    LinearLayout container = new LinearLayout(ctx);
    container.setOrientation(LinearLayout.HORIZONTAL);
    container.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

    GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.RECTANGLE);
    bg.setCornerRadius(dpToPx(28));
    bg.setColor(Color.parseColor("#EE1A1A2E"));
    bg.setStroke(dpToPx(1), Color.parseColor("#AA555555"));
    container.setBackground(bg);

    container.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            initialX = params.x;
            initialY = params.y;
            initialTouchX = event.getRawX();
            initialTouchY = event.getRawY();
            return true;
          case MotionEvent.ACTION_MOVE:
            params.x = initialX + (int) (event.getRawX() - initialTouchX);
            params.y = initialY + (int) (event.getRawY() - initialTouchY);
            windowManager.updateViewLayout(overlayView, params);
            return true;
          case MotionEvent.ACTION_UP:
            return true;
        }
        return false;
      }
    });

    if (MODE_INCOMING.equals(mode)) {
      container.addView(buildInfoLabel(ctx, "Incoming call", addr, true));
      container.addView(buildButton(ctx, "✓", Color.parseColor("#22C55E"),
        () -> sendEvent("onOverlayAccept", null)));
      container.addView(buildButton(ctx, "✗", Color.parseColor("#EF4444"),
        () -> sendEvent("onOverlayReject", null)));
    } else {
      container.addView(buildInfoLabel(ctx, "Call connected", addr, false));
      container.addView(buildButton(ctx, "✗", Color.parseColor("#EF4444"),
        () -> sendEvent("onOverlayEnd", null)));
    }

    return container;
  }

  private View buildInfoLabel(Context ctx, String title, String subtitle, boolean showIncoming) {
    LinearLayout wrapper = new LinearLayout(ctx);
    wrapper.setOrientation(LinearLayout.VERTICAL);
    wrapper.setPadding(0, 0, dpToPx(8), 0);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    lp.gravity = Gravity.CENTER_VERTICAL;
    wrapper.setLayoutParams(lp);

    TextView titleView = new TextView(ctx);
    titleView.setText(title);
    if (showIncoming) {
      titleView.setTextColor(Color.parseColor("#FCD34D"));
      titleView.setTextSize(13);
    } else {
      titleView.setTextColor(Color.parseColor("#4ADE80"));
      titleView.setTextSize(13);
    }
    wrapper.addView(titleView);

    TextView addrView = new TextView(ctx);
    String display = subtitle.length() > 16
      ? subtitle.substring(0, 16) + "…"
      : subtitle;
    addrView.setText(display);
    addrView.setTextColor(Color.LTGRAY);
    addrView.setTextSize(11);
    wrapper.addView(addrView);

    return wrapper;
  }

  private View buildButton(Context ctx, String label, int color, Runnable onClick) {
    int size = dpToPx(40);
    TextView btn = new TextView(ctx);
    btn.setLayoutParams(new LinearLayout.LayoutParams(size, size));
    btn.setText(label);
    btn.setTextColor(Color.WHITE);
    btn.setTextSize(18);
    btn.setGravity(Gravity.CENTER);
    btn.setOnClickListener(v -> onClick.run());

    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.OVAL);
    shape.setColor(color);
    btn.setBackground(shape);

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
    lp.setMargins(dpToPx(4), 0, 0, 0);
    lp.gravity = Gravity.CENTER_VERTICAL;
    btn.setLayoutParams(lp);

    return btn;
  }

  private int dpToPx(int dp) {
    return (int) (dp * getReactApplicationContext().getResources().getDisplayMetrics().density);
  }

  private void sendEvent(String eventName, WritableMap params) {
    getReactApplicationContext()
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params == null ? Arguments.createMap() : params);
  }

  private void hideOverlay() {
    if (overlayView != null && windowManager != null) {
      try {
        windowManager.removeView(overlayView);
      } catch (Exception e) {
        Log.w(TAG, "removeView error", e);
      }
      overlayView = null;
      windowManager = null;
    }
  }
}
