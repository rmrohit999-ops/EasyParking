import {
  ArrayMinSize,
  IsArray,
  IsBoolean,
  IsEnum,
  IsIn,
  IsInt,
  IsLatitude,
  IsLongitude,
  IsNumber,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';
import { ParkingType, VehicleCategory, VehicleType, VehicleSize, PhotoType, ListingStatus } from '@prisma/client';

// A listing's own vehicle-category-equivalent field lives at section level,
// not the listing — but sections may only be created against the same
// driver-selectable subset the vehicles module uses, for the same reason:
// UNSUPPORTED_PENDING_REVIEW is a backend/admin-assigned state, never
// something an owner declares directly (Milestone 0 §6).
export const OWNER_SELECTABLE_SECTION_CATEGORIES = [
  VehicleCategory.TWO_WHEELER,
  VehicleCategory.FOUR_WHEELER,
  VehicleCategory.OTHER_SUPPORTED,
] as const;

export class CreateListingDto {
  @IsString()
  @MinLength(2)
  @MaxLength(120)
  name!: string;

  @IsEnum(ParkingType)
  parkingType!: ParkingType;

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  description?: string;
}

export class UpdateListingDto {
  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(120)
  name?: string;

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  description?: string;
}

const OWNER_SETTABLE_LISTING_STATUSES = [ListingStatus.ACTIVE, ListingStatus.PAUSED, ListingStatus.CLOSED] as const;

export class UpdateListingStatusDto {
  @IsIn(OWNER_SETTABLE_LISTING_STATUSES)
  status!: (typeof OWNER_SETTABLE_LISTING_STATUSES)[number];
}

export class UpsertLocationDto {
  @IsLatitude()
  latitude!: number;

  @IsLongitude()
  longitude!: number;

  @IsString()
  @MinLength(4)
  @MaxLength(240)
  addressLine!: string;

  @IsString()
  @MinLength(2)
  @MaxLength(80)
  city!: string;

  @IsString()
  @MinLength(2)
  @MaxLength(80)
  state!: string;

  @IsString()
  @MinLength(3)
  @MaxLength(12)
  postalCode!: string;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  entranceNotes?: string;

  @IsOptional()
  @IsNumber()
  @Min(0)
  locationAccuracyMeters?: number;
}

export class CreateSectionDto {
  @IsString()
  @MinLength(1)
  @MaxLength(60)
  name!: string;

  @IsEnum(OWNER_SELECTABLE_SECTION_CATEGORIES)
  vehicleCategory!: (typeof OWNER_SELECTABLE_SECTION_CATEGORIES)[number];

  @IsArray()
  @ArrayMinSize(1)
  @IsEnum(VehicleType, { each: true })
  supportedVehicleTypes!: VehicleType[];

  @IsInt()
  @Min(1)
  @Max(5000)
  capacity!: number;

  // Minor units (paise) — never a float. 100 = ₹1.00; floor chosen so a
  // listing can't be created effectively free by mistake.
  @IsInt()
  @Min(100)
  hourlyRateMinorUnits!: number;

  @IsOptional()
  @IsBoolean()
  isCovered?: boolean;

  @IsOptional()
  @IsBoolean()
  hasSecurity?: boolean;

  @IsOptional()
  @IsBoolean()
  hasCctv?: boolean;

  @IsOptional()
  @IsBoolean()
  hasEvCharging?: boolean;

  @IsOptional()
  @IsBoolean()
  instantModeEnabled?: boolean;

  @IsOptional()
  @IsObject()
  operatingHours?: Record<string, unknown>;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  locationNotes?: string;
}

export class UpdateSectionDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(60)
  name?: string;

  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @IsEnum(VehicleType, { each: true })
  supportedVehicleTypes?: VehicleType[];

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(5000)
  capacity?: number;

  @IsOptional()
  @IsInt()
  @Min(100)
  hourlyRateMinorUnits?: number;

  @IsOptional()
  @IsBoolean()
  isCovered?: boolean;

  @IsOptional()
  @IsBoolean()
  hasSecurity?: boolean;

  @IsOptional()
  @IsBoolean()
  hasCctv?: boolean;

  @IsOptional()
  @IsBoolean()
  hasEvCharging?: boolean;

  @IsOptional()
  @IsBoolean()
  instantModeEnabled?: boolean;

  @IsOptional()
  @IsObject()
  operatingHours?: Record<string, unknown>;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  locationNotes?: string;

  // vehicleCategory is deliberately NOT editable after section creation —
  // capacity/booking history are keyed off it; changing it is a
  // close-and-recreate operation, not an update, to avoid orphaning
  // in-flight bookings against a section whose category just changed
  // under them.
}

const OWNER_SETTABLE_SECTION_STATUSES = [ListingStatus.ACTIVE, ListingStatus.PAUSED, ListingStatus.CLOSED] as const;

export class UpdateSectionStatusDto {
  @IsIn(OWNER_SETTABLE_SECTION_STATUSES)
  status!: (typeof OWNER_SETTABLE_SECTION_STATUSES)[number];
}

export class CreateSpaceDto {
  @IsString()
  @MinLength(1)
  @MaxLength(20)
  spaceLabel!: string;

  @IsOptional()
  @IsEnum(VehicleSize)
  size?: VehicleSize;

  @IsOptional()
  @IsInt()
  @Min(1)
  lengthCm?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  widthCm?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  heightClearanceCm?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  weightLimitKg?: number;

  @IsOptional()
  @IsBoolean()
  isEvCapable?: boolean;

  @IsOptional()
  @IsBoolean()
  isCovered?: boolean;

  @IsOptional()
  @IsBoolean()
  isAccessible?: boolean;
}

export class UpdateSpaceDto {
  @IsOptional()
  @IsEnum(VehicleSize)
  size?: VehicleSize;

  @IsOptional()
  @IsInt()
  @Min(1)
  lengthCm?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  widthCm?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  heightClearanceCm?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  weightLimitKg?: number;

  @IsOptional()
  @IsBoolean()
  isEvCapable?: boolean;

  @IsOptional()
  @IsBoolean()
  isCovered?: boolean;

  @IsOptional()
  @IsBoolean()
  isAccessible?: boolean;

  @IsOptional()
  @IsBoolean()
  active?: boolean;
}

const ALLOWED_PHOTO_CONTENT_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;

export class CreatePhotoUploadUrlDto {
  @IsEnum(PhotoType)
  photoType!: PhotoType;

  @IsIn(ALLOWED_PHOTO_CONTENT_TYPES)
  contentType!: (typeof ALLOWED_PHOTO_CONTENT_TYPES)[number];

  // Required (and validated against the listing) when photoType === SECTION.
  @IsOptional()
  @IsUUID()
  sectionId?: string;
}

export class RegisterPhotoDto {
  @IsString()
  @MinLength(3)
  storageKey!: string;

  @IsEnum(PhotoType)
  photoType!: PhotoType;

  @IsOptional()
  @IsUUID()
  sectionId?: string;
}

// Milestone 8 (QR & parking operations): who's authorized to assign an
// attendant, and how, is Milestone 0 §18's Open Question #7 ("attendant
// employment model") — never resolved by the user, so this implements the
// option the schema already leans toward (`attendant_profiles.
// employer_owner_id` nullable "if owner-hired") as the concrete default:
// an owner invites an existing account by email, which both grants that
// account ATTENDANT access (if it doesn't already have it) and creates the
// per-listing assignment in one step. A future admin-managed/centrally
// dispatched attendant pool (employer_owner_id staying null) is a separate,
// additive flow this doesn't foreclose.
export class AssignAttendantDto {
  @IsString()
  attendantEmail!: string;

  @IsArray()
  @ArrayMinSize(1)
  @IsEnum(VehicleCategory, { each: true })
  authorizedCategories!: VehicleCategory[];
}

export class AdminRejectDto {
  @IsString()
  @MinLength(3)
  @MaxLength(1000)
  reason!: string;
}

export class AdminRequestMoreInfoDto {
  @IsString()
  @MinLength(3)
  @MaxLength(1000)
  message!: string;
}
