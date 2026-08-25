/**
 * The same backend every mobile app in this project talks to — no
 * separate API surface for the web portal. Server-only: this file is
 * never imported by a Client Component, so the URL (not secret, but no
 * reason to ship it to the browser bundle either) stays server-side.
 */
export const API_BASE_URL = process.env.API_BASE_URL ?? "https://easyparking-production.up.railway.app";
