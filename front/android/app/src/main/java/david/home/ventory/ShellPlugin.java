package david.home.ventory;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.activity.result.ActivityResult;
import androidx.core.content.pm.PackageInfoCompat;
import com.getcapacitor.CapConfig;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * The native side of the app shell: which server the UI is loaded from, and a
 * native QR scanner (the web one needs a secure origin, which a plain-http
 * server on the LAN is not).
 */
@CapacitorPlugin(name = "HomeVentoryShell")
public class ShellPlugin extends Plugin {

    private static final String PREFS = "shell";
    private static final String KEY_SERVER = "server";
    // Served from the APK, so it shows even when the server is unreachable.
    private static final String ERROR_PATH = "shell-error.html";

    static String getServer(Context context) {
        return prefs(context).getString(KEY_SERVER, null);
    }

    /** The bundled capacitor.config.json, pointed at the server. Null if it can't be built. */
    static CapConfig configFor(Context context, String server) {
        try {
            JSONObject json = new JSONObject(readAsset(context, "capacitor.config.json"));
            JSONObject serverJson = json.optJSONObject("server");
            if (serverJson == null) serverJson = new JSONObject();
            serverJson.put("url", server);
            serverJson.put("errorPath", ERROR_PATH);
            json.put("server", serverJson);

            File dir = new File(context.getFilesDir(), "shell-config");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("can't create " + dir);
            try (FileOutputStream out = new FileOutputStream(new File(dir, "capacitor.config.json"))) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
            return CapConfig.loadFromFile(context, dir.getAbsolutePath());
        } catch (Exception e) {
            Logger.error("Couldn't point the app at " + server, e);
            return null;
        }
    }

    private static String readAsset(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Use `url` (http(s)://host[:port]) as the server and reload the app from it. */
    @PluginMethod
    public void connect(PluginCall call) {
        Uri uri = Uri.parse(call.getString("url", ""));
        String scheme = uri.getScheme();
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getHost().isEmpty()) {
            call.reject("Enter an address like http://192.168.0.10:3001");
            return;
        }
        String origin = scheme + "://" + uri.getEncodedAuthority();
        prefs(getContext()).edit().putString(KEY_SERVER, origin).commit();
        call.resolve();
        restart();
    }

    /** Forget the server and go back to the bundled address screen. */
    @PluginMethod
    public void disconnect(PluginCall call) {
        prefs(getContext()).edit().remove(KEY_SERVER).commit();
        call.resolve();
        restart();
    }

    @PluginMethod
    public void info(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("server", getServer(getContext()));
        try {
            Context c = getContext();
            ret.put("shellVersion", PackageInfoCompat.getLongVersionCode(c.getPackageManager().getPackageInfo(c.getPackageName(), 0)));
        } catch (Exception e) {
            ret.put("shellVersion", 0);
        }
        call.resolve(ret);
    }

    /** Scan one QR code. Resolves `{text}`, or `{}` when cancelled. */
    @PluginMethod
    public void scan(PluginCall call) {
        ScanOptions options = new ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Point the camera at a box sticker")
            .setBeepEnabled(false)
            .setOrientationLocked(false);
        startActivityForResult(call, new ScanContract().createIntent(getContext(), options), "onScanResult");
    }

    @ActivityCallback
    private void onScanResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        ScanIntentResult scan = ScanIntentResult.parseActivityResult(result.getResultCode(), result.getData());
        JSObject ret = new JSObject();
        if (scan.getContents() != null) ret.put("text", scan.getContents());
        call.resolve(ret);
    }

    private void restart() {
        getActivity().runOnUiThread(() -> getActivity().recreate());
    }
}
