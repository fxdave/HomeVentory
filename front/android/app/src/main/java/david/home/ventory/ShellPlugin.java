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
import java.net.HttpURLConnection;
import java.net.URL;
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
    private static final int TIMEOUT_MS = 8000;

    static String getServer(Context context) {
        return prefs(context).getString(KEY_SERVER, null);
    }

    static void forgetServer(Context context) {
        prefs(context).edit().remove(KEY_SERVER).commit();
    }

    /** The bundled capacitor.config.json, pointed at the server. Null if it can't be built. */
    static CapConfig configFor(Context context, String server) {
        try {
            JSONObject json = new JSONObject(readAsset(context, "capacitor.config.json"));
            JSONObject serverJson = json.optJSONObject("server");
            if (serverJson == null) serverJson = new JSONObject();
            serverJson.put("url", server);
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

    /**
     * Use `url` (http(s)://host[:port]) as the server and reload the app from it.
     * Checks first that it really is a HomeVentory server, so a typo can't leave
     * the app stuck on a page that isn't ours.
     */
    @PluginMethod
    public void connect(PluginCall call) {
        String origin = originOf(call.getString("url", ""));
        if (origin == null) {
            call.reject("Enter an address like http://192.168.0.10:3001");
            return;
        }
        new Thread(() -> {
            try {
                String server = checkServer(origin);
                prefs(getContext()).edit().putString(KEY_SERVER, server).commit();
                call.resolve();
                restart();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        }).start();
    }

    /** Forget the server and go back to the bundled address screen. */
    @PluginMethod
    public void disconnect(PluginCall call) {
        forgetServer(getContext());
        call.resolve();
        restart();
    }

    private static String originOf(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getHost().isEmpty()) {
            return null;
        }
        return scheme + "://" + uri.getEncodedAuthority();
    }

    /**
     * Fetches the server's PWA manifest and checks it is HomeVentory's. Follows
     * redirects (e.g. http -> https) and returns the origin it ended up on.
     */
    private static String checkServer(String origin) throws Exception {
        String current = origin;
        for (int redirects = 0; redirects < 4; redirects++) {
            HttpURLConnection connection;
            int status;
            try {
                connection = (HttpURLConnection) new URL(current + "/manifest.webmanifest").openConnection();
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                status = connection.getResponseCode();
            } catch (Exception e) {
                throw new Exception("Couldn't reach " + current + ". Check the address, and that the server is running.");
            }
            try {
                if (status >= 300 && status < 400 && connection.getHeaderField("Location") != null) {
                    String next = originOf(new URL(new URL(current), connection.getHeaderField("Location")).toString());
                    if (next == null) break;
                    current = next;
                    continue;
                }
                if (status == 200 && readUpTo(connection.getInputStream(), 64 * 1024).contains("HomeVentory")) {
                    return current;
                }
                break;
            } finally {
                connection.disconnect();
            }
        }
        throw new Exception(current + " isn't a HomeVentory server. Check the address and port.");
    }

    private static String readUpTo(InputStream in, int limit) throws Exception {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; out.size() < limit && (n = stream.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
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
