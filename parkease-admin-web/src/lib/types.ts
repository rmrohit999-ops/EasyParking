// Mirrors parkease-backend's actual response shapes 1:1 — same source of
// truth the Android admin screens' DTOs already mirror (core-network's
// AdminDtos.kt). Kept in one file since the portal's screen set is small.

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

export interface UserProfileResponse {
  id: string;
  email: string | null;
  phone: string | null;
  status: string;
  roles: string[];
}

export interface AdminDashboardSummaryResponse {
  totalUsers: number;
  suspendedUsers: number;
  pendingListings: number;
  openFraudAlerts: number;
  openSupportTickets: number;
  openDisputes: number;
}

export interface AdminUserSummaryResponse {
  id: string;
  email: string | null;
  phone: string | null;
  status: string;
  roles: string[];
}

export interface AdminPendingListingResponse {
  id: string;
  name: string;
  parkingType: string;
  approvalStatus: string;
}

export interface AdminCashByOwnerResponse {
  ownerId: string;
  businessName: string | null;
  phone: string | null;
  email: string | null;
  transactionCount: number;
  totalCashCollectedMinorUnits: string;
  commissionMinorUnits: string;
  netEarningsMinorUnits: string;
}

export interface AdminCashSummaryResponse {
  currency: string;
  totalCashCollectedMinorUnits: string;
  totalCommissionMinorUnits: string;
  totalOwnerNetMinorUnits: string;
  completedCount: number;
  pendingCount: number;
  byOwner: AdminCashByOwnerResponse[];
}

export interface MapsQuotaSkuUsageResponse {
  sku: string;
  count: number;
  cap: number;
  percentUsed: number;
  capReached: boolean;
}

export interface MapsQuotaSnapshotResponse {
  date: string;
  globallyTripped: boolean;
  skus: MapsQuotaSkuUsageResponse[];
}
