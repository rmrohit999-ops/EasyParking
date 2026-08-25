import { apiFetch } from "@/lib/api";
import type { AdminCashSummaryResponse } from "@/lib/types";
import { formatMinorUnits } from "@/lib/money";

export default async function CashPage() {
  const summary = await apiFetch<AdminCashSummaryResponse>("/v1/admin/reports/cash-summary");

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Cash payments</h1>
      <p className="mt-1 text-sm text-slate-500">Total collected, owner-wise breakdown, pending vs completed.</p>

      <div className="mt-6 grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Total collected" value={formatMinorUnits(summary.totalCashCollectedMinorUnits, summary.currency)} />
        <SummaryCard label="ParkEase commission" value={formatMinorUnits(summary.totalCommissionMinorUnits, summary.currency)} />
        <SummaryCard label="Owner net payable" value={formatMinorUnits(summary.totalOwnerNetMinorUnits, summary.currency)} />
      </div>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <SummaryCard label="Completed" value={String(summary.completedCount)} accent="text-emerald-700" />
        <SummaryCard label="Pending" value={String(summary.pendingCount)} accent="text-amber-700" />
      </div>

      <h2 className="mt-8 text-sm font-semibold uppercase tracking-wide text-slate-500">By owner</h2>
      <div className="mt-3 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <table className="min-w-full">
          <thead className="border-b border-slate-200 bg-slate-50">
            <tr>
              <th className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Owner</th>
              <th className="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Transactions</th>
              <th className="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Collected</th>
              <th className="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Commission</th>
              <th className="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Net earnings</th>
            </tr>
          </thead>
          <tbody>
            {summary.byOwner.map((owner) => (
              <tr key={owner.ownerId} className="border-b border-slate-100 last:border-0">
                <td className="px-4 py-3 text-sm text-slate-900">{owner.businessName ?? owner.email ?? owner.phone ?? "Owner"}</td>
                <td className="px-4 py-3 text-right text-sm text-slate-600">{owner.transactionCount}</td>
                <td className="px-4 py-3 text-right text-sm text-slate-600">{formatMinorUnits(owner.totalCashCollectedMinorUnits, summary.currency)}</td>
                <td className="px-4 py-3 text-right text-sm text-slate-600">{formatMinorUnits(owner.commissionMinorUnits, summary.currency)}</td>
                <td className="px-4 py-3 text-right text-sm text-slate-600">{formatMinorUnits(owner.netEarningsMinorUnits, summary.currency)}</td>
              </tr>
            ))}
            {summary.byOwner.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-sm text-slate-400">
                  No completed cash transactions in this period.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function SummaryCard({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <p className={`text-xl font-bold ${accent ?? "text-slate-900"}`}>{value}</p>
      <p className="mt-1 text-xs text-slate-500">{label}</p>
    </div>
  );
}
