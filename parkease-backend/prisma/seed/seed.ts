import { PrismaClient } from '@prisma/client';

/**
 * Development-only seed data. Deliberately refuses to run outside
 * development, per Milestone 0's "development-only seed data must be
 * clearly separated from production data" / "never seed fake production
 * transactions or fake successful payments" requirement.
 *
 * Milestone 1 seeds only a minimal, structurally-valid feature flag row to
 * prove the migration + seed pipeline works end-to-end. Seeding of
 * users/vehicles/parking/bookings is added incrementally as each
 * milestone's models gain real service logic — seeding fake bookings or
 * fake payment successes ahead of that would violate the "never simulate
 * success" rule.
 */
const prisma = new PrismaClient();

async function main() {
  if (process.env.NODE_ENV === 'production') {
    throw new Error('Refusing to run seed script with NODE_ENV=production.');
  }

  await prisma.featureFlag.upsert({
    where: { key: 'instant_parking' },
    update: {},
    create: {
      key: 'instant_parking',
      description: 'Enables the Instant Parking booking flow.',
      enabled_globally: false,
    },
  });

  await prisma.featureFlag.upsert({
    where: { key: 'cash_payments' },
    update: {},
    create: {
      key: 'cash_payments',
      description: 'Enables the cash payment collection workflow.',
      enabled_globally: false,
    },
  });

  // eslint-disable-next-line no-console
  console.log('Dev seed complete (feature flags only — no fake users/bookings/payments).');
}

main()
  .catch((e) => {
    // eslint-disable-next-line no-console
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
