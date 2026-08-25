import { requireAccessToken } from "@/lib/session";
import { Sidebar } from "./Sidebar";

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  // Redirects to /login if there's no session cookie at all. The real
  // authorization boundary is still every individual page's own backend
  // call, which re-checks role from the database on every request — this
  // is only "is someone signed in," not "is this specific action allowed."
  await requireAccessToken();

  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <main className="flex-1 overflow-x-auto p-8">{children}</main>
    </div>
  );
}
