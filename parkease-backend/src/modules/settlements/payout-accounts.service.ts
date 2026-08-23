import { BadRequestException, ConflictException, ForbiddenException, Injectable, NotFoundException, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OwnerPayoutAccount } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AppConfig } from '../../common/config/configuration';
import { decryptField, encryptField, maskAccountNumber } from '../../common/crypto/field-encryption';
import { CreatePayoutAccountDto, VerifyPayoutAccountDto } from './dto/settlements.dto';

/**
 * Owner-facing bank/UPI destination management (Milestone 9). The raw
 * account number / UPI VPA is encrypted at rest (field-encryption.ts) and
 * never returned to any client, including the owning owner — only a
 * masked view. Verification is a real, distinct admin action
 * (`adminVerify`) rather than auto-VERIFIED on creation: this sandbox has
 * no bank-penny-drop or UPI-validation integration to verify against for
 * real, so leaving it NOT_STARTED until a human (admin, standing in for a
 * KYC/verification back-office in a real deployment) confirms it is the
 * "no fakes" choice — auto-verifying would be pretending a check happened
 * that didn't.
 */
@Injectable()
export class PayoutAccountsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService<AppConfig, true>,
  ) {}

  async create(ownerUserId: string, dto: CreatePayoutAccountDto) {
    const ownerProfile = await this.requireOwnerProfile(ownerUserId);
    const encryption = this.configService.get('payoutEncryption', { infer: true });
    if (!encryption.isConfigured) {
      throw new ServiceUnavailableException('Payout account setup is temporarily unavailable. Please try again later.');
    }
    if (dto.method === 'BANK' && (!dto.accountNumber || !dto.ifsc)) {
      throw new BadRequestException('accountNumber and ifsc are required for a bank payout account.');
    }
    if (dto.method === 'UPI' && !dto.upiVpa) {
      throw new BadRequestException('upiVpa is required for a UPI payout account.');
    }

    const isFirst = (await this.prisma.ownerPayoutAccount.count({ where: { owner_id: ownerProfile.id } })) === 0;

    const created = await this.prisma.ownerPayoutAccount.create({
      data: {
        owner_id: ownerProfile.id,
        method: dto.method,
        account_holder_name: dto.accountHolderName,
        bank_account_number_encrypted: dto.accountNumber ? encryptField(dto.accountNumber, encryption.key) : null,
        ifsc: dto.ifsc ?? null,
        upi_vpa_encrypted: dto.upiVpa ? encryptField(dto.upiVpa, encryption.key) : null,
        verification_status: 'NOT_STARTED',
        is_primary: isFirst,
      },
    });
    return this.toView(created);
  }

  async list(ownerUserId: string) {
    const ownerProfile = await this.requireOwnerProfile(ownerUserId);
    const accounts = await this.prisma.ownerPayoutAccount.findMany({
      where: { owner_id: ownerProfile.id },
      orderBy: { created_at: 'asc' },
    });
    return accounts.map((a) => this.toView(a));
  }

  async setPrimary(ownerUserId: string, accountId: string) {
    const ownerProfile = await this.requireOwnerProfile(ownerUserId);
    const account = await this.prisma.ownerPayoutAccount.findUnique({ where: { id: accountId } });
    if (!account || account.owner_id !== ownerProfile.id) throw new NotFoundException('Payout account not found.');

    await this.prisma.$transaction([
      this.prisma.ownerPayoutAccount.updateMany({ where: { owner_id: ownerProfile.id, is_primary: true }, data: { is_primary: false } }),
      this.prisma.ownerPayoutAccount.update({ where: { id: accountId }, data: { is_primary: true } }),
    ]);
    return this.toView({ ...account, is_primary: true });
  }

  async remove(ownerUserId: string, accountId: string) {
    const ownerProfile = await this.requireOwnerProfile(ownerUserId);
    const account = await this.prisma.ownerPayoutAccount.findUnique({ where: { id: accountId } });
    if (!account || account.owner_id !== ownerProfile.id) throw new NotFoundException('Payout account not found.');

    const inFlight = await this.prisma.settlement.findFirst({
      where: { payout_account_id: accountId, status: { in: ['PENDING', 'PROCESSING'] } },
    });
    if (inFlight) throw new ConflictException('This payout account has a settlement in progress and cannot be removed yet.');

    await this.prisma.ownerPayoutAccount.delete({ where: { id: accountId } });

    if (account.is_primary) {
      const next = await this.prisma.ownerPayoutAccount.findFirst({ where: { owner_id: ownerProfile.id }, orderBy: { created_at: 'asc' } });
      if (next) await this.prisma.ownerPayoutAccount.update({ where: { id: next.id }, data: { is_primary: true } });
    }
    return { id: accountId, removed: true };
  }

  async adminVerify(accountId: string, dto: VerifyPayoutAccountDto) {
    const account = await this.prisma.ownerPayoutAccount.findUnique({ where: { id: accountId } });
    if (!account) throw new NotFoundException('Payout account not found.');
    const updated = await this.prisma.ownerPayoutAccount.update({
      where: { id: accountId },
      data: { verification_status: dto.verificationStatus },
    });
    return this.toView(updated);
  }

  private toView(account: OwnerPayoutAccount) {
    const encryption = this.configService.get('payoutEncryption', { infer: true });
    const canDecrypt = encryption.isConfigured;
    return {
      id: account.id,
      method: account.method,
      accountHolderName: account.account_holder_name,
      accountNumberMasked:
        account.bank_account_number_encrypted && canDecrypt
          ? maskAccountNumber(decryptField(account.bank_account_number_encrypted, encryption.key))
          : null,
      ifsc: account.ifsc,
      upiVpaMasked: account.upi_vpa_encrypted && canDecrypt ? maskAccountNumber(decryptField(account.upi_vpa_encrypted, encryption.key)) : null,
      verificationStatus: account.verification_status,
      isPrimary: account.is_primary,
      createdAt: account.created_at,
    };
  }

  private async requireOwnerProfile(userId: string) {
    const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: userId } });
    if (!ownerProfile) throw new ForbiddenException('You do not have an owner profile.');
    return ownerProfile;
  }
}
