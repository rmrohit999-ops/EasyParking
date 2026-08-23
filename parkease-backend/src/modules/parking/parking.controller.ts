import { Body, Controller, Delete, Get, Param, Patch, Post, Put } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { CheckOwnership } from '../../common/guards/resource-ownership.guard';
import { ParkingService } from './parking.service';
import {
  AssignAttendantDto,
  CreateListingDto,
  CreatePhotoUploadUrlDto,
  CreateSectionDto,
  CreateSpaceDto,
  RegisterPhotoDto,
  UpdateListingDto,
  UpdateListingStatusDto,
  UpdateSectionDto,
  UpdateSectionStatusDto,
  UpdateSpaceDto,
  UpsertLocationDto,
} from './dto/parking.dto';

/**
 * Every nested resource here (section, space, photo) is reached through a
 * URL that includes :listingId, so a single ownership check —
 * ParkingListingOwnershipResolver keyed on :listingId — covers the whole
 * tree; the service layer additionally verifies a :sectionId/:spaceId
 * really belongs to that :listingId (defense in depth against an owner
 * passing a listingId they own alongside a sectionId they don't).
 */
@ApiTags('parking')
@Roles('OWNER')
@Controller({ path: 'parking-listings', version: '1' })
export class ParkingListingsController {
  constructor(private readonly parkingService: ParkingService) {}

  @Post()
  @ApiOperation({ summary: 'Create a new parking listing (starts PENDING approval, PAUSED)' })
  create(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateListingDto) {
    return this.parkingService.createListing(user.id, dto);
  }

  @Get()
  @ApiOperation({ summary: "List the current owner's parking listings" })
  listMine(@CurrentUser() user: AuthenticatedUser) {
    return this.parkingService.listMine(user.id);
  }

  @Get(':listingId')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Get one listing with its location, sections and photo count' })
  getOne(@Param('listingId') listingId: string) {
    return this.parkingService.getOne(listingId);
  }

  @Patch(':listingId')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Edit listing name/description' })
  update(@Param('listingId') listingId: string, @Body() dto: UpdateListingDto) {
    return this.parkingService.updateListing(listingId, dto);
  }

  @Patch(':listingId/status')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Activate, pause, or close a listing (ACTIVE requires prior admin approval)' })
  updateStatus(@Param('listingId') listingId: string, @Body() dto: UpdateListingStatusDto) {
    return this.parkingService.updateListingStatus(listingId, dto);
  }

  @Put(':listingId/location')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Set or update the pinned map location for a listing' })
  upsertLocation(@Param('listingId') listingId: string, @Body() dto: UpsertLocationDto) {
    return this.parkingService.upsertLocation(listingId, dto);
  }

  @Post(':listingId/submit-for-approval')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Submit a completed listing for admin review' })
  submitForApproval(@Param('listingId') listingId: string) {
    return this.parkingService.submitForApproval(listingId);
  }

  @Post(':listingId/sections')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Add a parking section (fixed vehicle category, capacity)' })
  createSection(@Param('listingId') listingId: string, @Body() dto: CreateSectionDto) {
    return this.parkingService.createSection(listingId, dto);
  }

  @Get(':listingId/sections')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'List sections for a listing' })
  listSections(@Param('listingId') listingId: string) {
    return this.parkingService.listSections(listingId);
  }

  @Patch(':listingId/sections/:sectionId')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Edit a section (category is fixed at creation)' })
  updateSection(
    @Param('listingId') listingId: string,
    @Param('sectionId') sectionId: string,
    @Body() dto: UpdateSectionDto,
  ) {
    return this.parkingService.updateSection(listingId, sectionId, dto);
  }

  @Patch(':listingId/sections/:sectionId/status')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Activate, pause, or close a section (ACTIVE requires prior admin approval)' })
  updateSectionStatus(
    @Param('listingId') listingId: string,
    @Param('sectionId') sectionId: string,
    @Body() dto: UpdateSectionStatusDto,
  ) {
    return this.parkingService.updateSectionStatus(listingId, sectionId, dto);
  }

  @Delete(':listingId/sections/:sectionId')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Delete a section (only if it has no booking history)' })
  removeSection(@Param('listingId') listingId: string, @Param('sectionId') sectionId: string) {
    return this.parkingService.removeSection(listingId, sectionId);
  }

  @Post(':listingId/sections/:sectionId/spaces')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Add an individually tracked space within a section' })
  createSpace(
    @Param('listingId') listingId: string,
    @Param('sectionId') sectionId: string,
    @Body() dto: CreateSpaceDto,
  ) {
    return this.parkingService.createSpace(listingId, sectionId, dto);
  }

  @Get(':listingId/sections/:sectionId/spaces')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'List spaces within a section' })
  listSpaces(@Param('listingId') listingId: string, @Param('sectionId') sectionId: string) {
    return this.parkingService.listSpaces(listingId, sectionId);
  }

  @Patch(':listingId/sections/:sectionId/spaces/:spaceId')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Edit a space' })
  updateSpace(
    @Param('listingId') listingId: string,
    @Param('sectionId') sectionId: string,
    @Param('spaceId') spaceId: string,
    @Body() dto: UpdateSpaceDto,
  ) {
    return this.parkingService.updateSpace(listingId, sectionId, spaceId, dto);
  }

  @Delete(':listingId/sections/:sectionId/spaces/:spaceId')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Remove a space (only while AVAILABLE)' })
  removeSpace(
    @Param('listingId') listingId: string,
    @Param('sectionId') sectionId: string,
    @Param('spaceId') spaceId: string,
  ) {
    return this.parkingService.removeSpace(listingId, sectionId, spaceId);
  }

  @Post(':listingId/photos/upload-url')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Get a short-lived presigned URL to upload a photo directly to storage' })
  createPhotoUploadUrl(
    @CurrentUser() user: AuthenticatedUser,
    @Param('listingId') listingId: string,
    @Body() dto: CreatePhotoUploadUrlDto,
  ) {
    return this.parkingService.createPhotoUploadUrl(listingId, user.id, dto);
  }

  @Post(':listingId/photos')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Register a photo after it has been uploaded to storage' })
  registerPhoto(
    @CurrentUser() user: AuthenticatedUser,
    @Param('listingId') listingId: string,
    @Body() dto: RegisterPhotoDto,
  ) {
    return this.parkingService.registerPhoto(listingId, user.id, dto);
  }

  @Get(':listingId/photos')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'List photos with short-lived view URLs' })
  listPhotos(@Param('listingId') listingId: string) {
    return this.parkingService.listPhotos(listingId);
  }

  @Delete(':listingId/photos/:photoId')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Remove a photo' })
  removePhoto(@Param('listingId') listingId: string, @Param('photoId') photoId: string) {
    return this.parkingService.removePhoto(listingId, photoId);
  }

  // -- Attendants (Milestone 8) --------------------------------------

  @Post(':listingId/attendants')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Invite an existing account as an attendant for this listing (grants ATTENDANT access)' })
  assignAttendant(
    @CurrentUser() user: AuthenticatedUser,
    @Param('listingId') listingId: string,
    @Body() dto: AssignAttendantDto,
  ) {
    return this.parkingService.assignAttendant(user.id, listingId, dto);
  }

  @Get(':listingId/attendants')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'List active attendants assigned to this listing' })
  listAttendants(@Param('listingId') listingId: string) {
    return this.parkingService.listAttendants(listingId);
  }

  @Delete(':listingId/attendants/:assignmentId')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Revoke an attendant assignment' })
  revokeAttendant(@Param('listingId') listingId: string, @Param('assignmentId') assignmentId: string) {
    return this.parkingService.revokeAttendant(listingId, assignmentId);
  }
}
