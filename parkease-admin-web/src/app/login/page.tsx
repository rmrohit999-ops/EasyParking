import { LoginForm } from "./LoginForm";

export default function LoginPage() {
  return (
    <div className="flex min-h-screen flex-1 items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="mb-6 text-center">
          <h1 className="text-xl font-bold text-slate-900">ParkEase Admin</h1>
          <p className="mt-1 text-sm text-slate-500">Sign in with your admin account</p>
        </div>
        <LoginForm />
      </div>
    </div>
  );
}
