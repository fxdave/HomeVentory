import {Navigate, Route, Routes} from "react-router-dom";
import React, {ReactElement} from "react";
import {Boundary} from "@cuple/react";
import {useAuth} from "services/useAuth";
import {isBundledShell} from "services/shell";

export const ROUTES = {
  LOGIN: "/login",
  STICKERS: "/stickers",
  QR_SCANNER: "/qr-scanner",
  ITEMS: (subPath?: string) => `/items${subPath ? "/" + subPath : ""}`,
  OPEN_ITEM: (itemId: string) => `/open-item/${itemId}`,
};

const Login = React.lazy(() => import("./modules/Auth/LoginPage"));
const Items = React.lazy(() => import("./modules/Items/ItemsPage"));
const StickerPage = React.lazy(() => import("./modules/Sticker/StickerPage"));
const QRScannerPage = React.lazy(
  () => import("./modules/QRScanner/QRScannerPage"),
);

const withLoader = (element: ReactElement) => (
  <Boundary fallback={<></>}>{element}</Boundary>
);

export default function Router() {
  const auth = useAuth();
  // The app's bundled UI only picks the server; logging in happens on the
  // server's copy of the UI.
  if (auth.isLoggedIn && !isBundledShell) {
    return (
      <>
        <Routes>
          <Route path={ROUTES.ITEMS("*")} element={withLoader(<Items />)} />
          <Route
            path={ROUTES.OPEN_ITEM(":id")}
            element={withLoader(<Items />)}
          />
          <Route path={ROUTES.STICKERS} element={withLoader(<StickerPage />)} />
          <Route
            path={ROUTES.QR_SCANNER}
            element={withLoader(<QRScannerPage />)}
          />
          <Route path="*" element={<Navigate to={ROUTES.ITEMS()} replace />} />
        </Routes>
      </>
    );
  } else {
    return (
      <Routes>
        <Route path={ROUTES.LOGIN} element={withLoader(<Login />)} />
        <Route path="*" element={<Navigate to={ROUTES.LOGIN} replace />} />
      </Routes>
    );
  }
}
