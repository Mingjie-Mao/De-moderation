import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // vinext emits a self-contained runtime tree for Docker/VM deployments.
  // Pages uses a static export; Docker keeps the standalone runtime by default.
  output: process.env.ADMIN_STATIC_EXPORT === 'true' ? 'export' : 'standalone',
};

export default nextConfig;
