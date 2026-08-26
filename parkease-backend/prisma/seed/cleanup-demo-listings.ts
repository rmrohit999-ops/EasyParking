/**
 * Removes everything created by seed-demo-listings.ts — every User whose
 * email ends in @parkease.test (the reserved-TLD marker, see that script's
 * doc comment), plus their OwnerProfile and ParkingListing (which cascades
 * to ParkingLocation/ParkingSection/ParkingPhoto automatically per
 * schema.prisma's onDelete: Cascade on those child relations).
 *
 * ParkingListing -> OwnerProfile has NO cascade delete (schema.prisma line
 * ~590), so listings must be deleted before their owner profile — deleting
 * the User first would otherwise fail with a foreign key violation once it
 * tries to cascade into OwnerProfile while listings still reference it.
 *
 * Usage:
 *   DATABASE_URL="<target db>" npx ts-node prisma/seed/cleanup-demo-listings.ts --yes
 */
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  const confirmed = process.argv.includes('--yes');
  const dbHost = new URL(process.env.DATABASE_URL ?? '').host || '(unknown — DATABASE_URL not set)';

  const testUsers = await prisma.user.findMany({
    where: { email: { endsWith: '@parkease.test' } },
    include: { owner_profile: true },
  });

  if (testUsers.length === 0) {
    console.log(`No @parkease.test demo accounts found on ${dbHost}. Nothing to clean up.`);
    return;
  }

  console.log(`Found ${testUsers.length} demo account(s) on ${dbHost}:`);
  testUsers.forEach((u) => console.log(`  ${u.email}`));

  if (!confirmed) {
    console.log(
      'Refusing to delete without --yes — re-run with --yes once the list above looks right.',
    );
    process.exit(1);
  }

  const ownerProfileIds = testUsers
    .map((u) => u.owner_profile?.id)
    .filter((id): id is string => Boolean(id));

  const { count: listingsDeleted } = await prisma.parkingListing.deleteMany({
    where: { owner_id: { in: ownerProfileIds } },
  });
  const { count: usersDeleted } = await prisma.user.deleteMany({
    where: { id: { in: testUsers.map((u) => u.id) } },
  });

  console.log(
    `Deleted ${listingsDeleted} listing(s) (and their locations/sections/photos via cascade) and ${usersDeleted} user(s) (and their owner profiles via cascade).`,
  );
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
