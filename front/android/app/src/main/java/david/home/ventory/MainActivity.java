package david.home.ventory;

import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import androidx.appcompat.app.AlertDialog;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {

    private AlertDialog serverErrorDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(ShellPlugin.class);
        // With a server chosen, load the UI from it so the app always matches the
        // server's version. Without one, the bundled UI asks for the address.
        String server = ShellPlugin.getServer(this);
        if (server != null) {
            config = ShellPlugin.configFor(this, server);
        }
        super.onCreate(savedInstanceState);
        if (server != null && bridge != null) {
            bridge.setWebViewClient(new ServerErrorClient(server));
        }
    }

    /**
     * Native, because a page can't help here: a local error page gets no
     * Capacitor bridge (it isn't on the server's origin), so its buttons can't
     * reach the app.
     */
    private class ServerErrorClient extends BridgeWebViewClient {

        private final String server;

        ServerErrorClient(String server) {
            super(bridge);
            this.server = server;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) showServerError(server, String.valueOf(error.getDescription()));
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            super.onReceivedHttpError(view, request, response);
            if (request.isForMainFrame()) showServerError(server, "HTTP " + response.getStatusCode());
        }
    }

    private void showServerError(String server, String reason) {
        runOnUiThread(() -> {
            if (isFinishing() || (serverErrorDialog != null && serverErrorDialog.isShowing())) return;
            serverErrorDialog = new AlertDialog.Builder(this)
                .setTitle("Can't reach your server")
                .setMessage(
                    "Couldn't load " + server + " (" + reason + ").\n\n" +
                    "Check that the server is running and that this phone is on the same network."
                )
                .setCancelable(false)
                .setPositiveButton("Try again", (d, w) -> bridge.getWebView().loadUrl(bridge.getAppUrl()))
                .setNegativeButton("Use a different server", (d, w) -> {
                    ShellPlugin.forgetServer(this);
                    recreate();
                })
                .show();
        });
    }

    @Override
    public void onDestroy() {
        if (serverErrorDialog != null) serverErrorDialog.dismiss();
        super.onDestroy();
    }
}
