import {styled} from "styled-system/jsx";
import {useForm} from "react-hook-form";
import {useNavigate} from "react-router-dom";
import {ROUTES} from "Router";
import {useAuth} from "services/useAuth";
import {useEffect} from "react";
import {Alert} from "@ui/Alert";
import {Button} from "@ui/Button";
import {BrandSticker} from "modules/Common/BrandSticker";
import {TextField} from "@ui/Input";
import {hasShell, isBundledShell, Shell, withScheme} from "services/shell";

type IFields = {
  target: string;
  password: string;
};

const API_TARGET_KEY = "API_TARGET";

function defaultTarget() {
  const saved = localStorage.getItem(API_TARGET_KEY);
  if (saved) return saved;
  // Served by the HomeVentory server itself: the API is on the same origin.
  return isBundledShell ? "" : `${window.location.origin}/api`;
}

export default function LoginPage() {
  const {handleSubmit, register, setError, setValue, formState} =
    useForm<IFields>({
      defaultValues: async () => ({
        target: defaultTarget(),
        password: "",
      }),
    });

  useEffect(() => {
    if (window.location.href.includes("localhost:3000"))
      setValue("target", "http://localhost:3001/api");
  }, []);

  const auth = useAuth();
  const navigate = useNavigate();
  const rootError = formState.errors.root?.message;
  const onSubmit = handleSubmit(data => {
    if (isBundledShell) {
      const url = withScheme(data.target);
      localStorage.setItem(API_TARGET_KEY, url);
      // Checks the server, then restarts the app on the server's own copy of
      // the UI, which logs in. Returned so the button shows it's checking.
      return Shell.connect({url}).catch(e =>
        setError("root", {message: e instanceof Error ? e.message : `${e}`}),
      );
    }
    auth
      .login(data.target, data.password)
      .then(isSuccess => {
        localStorage.setItem(API_TARGET_KEY, data.target);

        if (isSuccess) {
          navigate(ROUTES.ITEMS(""));
        } else {
          setError("root", {
            message: "Couldn't log in",
          });
        }
      })
      .catch(e => {
        setError("root", {
          message: e instanceof Error ? e.message : e,
        });
      });
  });
  return (
    <Container>
      <Brand>
        <img src="/logo.svg" alt="" width={136} height={136} />
        <Title>
          <BrandSticker size="large" />
        </Title>
        <Subtitle>Connect to your inventory server.</Subtitle>
      </Brand>
      <Form onSubmit={onSubmit}>
        {!auth.isLoggedIn && auth.error && (
          <Alert severity="error">{auth.error}</Alert>
        )}
        {isBundledShell && rootError && (
          <Alert severity="error">{rootError}</Alert>
        )}
        <TextField
          label="Server address"
          {...register("target")}
          name="target"
          inputMode="url"
          autoComplete="url"
          helperText={
            isBundledShell
              ? "For example http://192.168.0.10:3001"
              : "For example http://192.168.0.10:3001/api"
          }
        />
        {!isBundledShell && (
          <TextField
            label="Password"
            helperText="First time? The password you enter here becomes the server password."
            {...register("password")}
            name="password"
            type="password"
            autoComplete="current-password"
          />
        )}
        <Button type="submit" disabled={formState.isSubmitting}>
          {formState.isSubmitting ? "Checking…" : "Connect"}
        </Button>
        {hasShell && !isBundledShell && (
          <Button
            type="button"
            variant="secondary"
            onClick={() => void Shell.disconnect()}>
            Use a different server
          </Button>
        )}
      </Form>
    </Container>
  );
}

const Container = styled("div", {
  base: {
    maxWidth: "400px",
    margin: "0 auto",
    padding: "56px 16px 32px",
    width: "100%",
  },
});

const Brand = styled("div", {
  base: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: "8px",
    marginBottom: "32px",
    textAlign: "center",
  },
});

const Title = styled("h1", {
  base: {
    margin: "8px 0 4px",
  },
});

const Subtitle = styled("p", {
  base: {
    margin: 0,
    color: "text.secondary",
  },
});

const Form = styled("form", {
  base: {
    flexDirection: "column",
    display: "flex",
    gap: "20px",
  },
});
