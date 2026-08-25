import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // This app lives inside the parkease monorepo, which has its own
  // top-level package-lock.json — without this, Next/Turbopack guesses at
  // the workspace root from lockfile detection and warns on every build.
  turbopack: {
    root: path.join(__dirname),
  },
};

export default nextConfig;
