import { apiFetch } from "@/lib/api";
import type { AdminDashboardSummaryResponse } from "@/lib/types";

const STAT_LABELS: Record<keyof AdminDashboardSummaryResponse, string> = {
  totalUsers: "Total users",
  suspendedUsers: "Suspended",
  pendingListings: "Pending listings",
  openFraudAlerts: "Fraud alerts",
  openSupportTickets: "Support tickets",
  openDisputes: "Open disputes",
};

export default async function DashboardPage() {
  const summary = await apiFetch<AdminDashboardSummaryResponse>("/v1/admin/dashboard/summary");

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Platform overview</h1>
      <p className="mt-1 text-sm text-slate-500">Real-time operational counts.</p>

      <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        {(Object.keys(STAT_LABELS) as Array<keyof AdminDashboardSummaryResponse>).map((key) => (
          <div key={key} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-2xl font-bold text-slate-900">{summary[key]}</p>
            <p className="mt-1 text-xs text-slate-500">{STAT_LABELS[key]}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
