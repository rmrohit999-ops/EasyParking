import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { UpdateProfileDto, BecomeOwnerDto } from './dto/update-profile.dto';

@Injectable()
export class UsersService {
  constructor(private readonly prisma: PrismaService) {}

  async getMe(userId: string) {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      include: { roles: { where: { status: 'ACTIVE' } }, driver_profile: true, owner_profile: true, attendant_profile: true },
    });
    if (!user) throw new NotFoundException('Account not found.');
    return this.toPublicProfile(user);
  }

  async updateMe(userId: string, dto: UpdateProfileDto) {
    const user = await this.prisma.user.update({
      where: { id: userId },
      data: { profile_photo_url: dto.profilePhotoUrl },
      include: { roles: { where: { status: 'ACTIVE' } } },
    });
    // fullName isn't a User column in this schema (kept on driver/owner
    // profile-specific tables per the ERD) — updating it here is a no-op
    // placeholder until Milestone 3/4 profile endpoints own that field.
    return this.toPublicProfile(user);
  }

  /**
   * Explicit self-upgrade to OWNER — never implicit at registration. Kicks
   * off the KYC/verification lifecycle from Milestone 0 §3.3 (verification
   * starts NOT_STARTED; a listing can't go public until APPROVED).
   */
  async becomeOwner(userId: string, dto: BecomeOwnerDto) {
    const existingRole = await this.prisma.userRoleAssignment.findUnique({
      where: { user_id_role: { user_id: userId, role: 'OWNER' } },
    });
    if (existingRole && existingRole.status === 'ACTIVE') {
      throw new ConflictException('This account already has owner access.');
    }

    await this.prisma.$transaction([
      this.prisma.userRoleAssignment.upsert({
        where: { user_id_role: { user_id: userId, role: 'OWNER' } },
        update: { status: 'ACTIVE' },
        create: { user_id: userId, role: 'OWNER' },
      }),
      this.prisma.ownerProfile.upsert({
        where: { user_id: userId },
        update: {},
        create: { user_id: userId, business_name: dto.businessName, verification_status: 'NOT_STARTED' },
      }),
    ]);

    return this.getMe(userId);
  }

  private toPublicProfile(user: {
    id: string;
    email: string | null;
    phone: string | null;
    profile_photo_url: string | null;
    status: string;
    roles: { role: string }[];
    created_at: Date;
  }) {
    return {
      id: user.id,
      email: user.email,
      phone: user.phone,
      profilePhotoUrl: user.profile_photo_url,
      status: user.status,
      roles: user.roles.map((r) => r.role),
      createdAt: user.created_at,
    };
  }
}
