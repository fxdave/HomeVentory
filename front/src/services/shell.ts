import {Capacitor, registerPlugin} from "@capacitor/core";

/** The Android app's native side (front/android/.../ShellPlugin.java). */
type ShellPlugin = {
  /** Load the UI from this server from now on (restarts the app). */
  connect(options: {url: string}): Promise<void>;
  /** Forget the server and show the bundled address screen (restarts the app). */
  disconnect(): Promise<void>;
  info(): Promise<{server: string | null; shellVersion: number}>;
  /** Native QR scanner; `text` is missing when cancelled. */
  scan(): Promise<{text?: string}>;
};

export const Shell = registerPlugin<ShellPlugin>("HomeVentoryShell");

/** Inside the Android app, and the app has the shell plugin (older apps don't). */
export const hasShell =
  Capacitor.isNativePlatform() &&
  Capacitor.isPluginAvailable("HomeVentoryShell");

/**
 * The copy of the UI bundled in the app. It only asks for the server address;
 * the real UI is then loaded from the server, so it always matches its version.
 */
export const isBundledShell =
  hasShell && window.location.origin === "https://localhost";

/** `192.168.0.10:3001/api` -> `http://192.168.0.10:3001/api` */
export function withScheme(address: string) {
  const trimmed = address.trim();
  return trimmed.includes("://") ? trimmed : `http://${trimmed}`;
}
