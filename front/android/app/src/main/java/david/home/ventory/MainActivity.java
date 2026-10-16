package david.home.ventory;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

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
    }
}
