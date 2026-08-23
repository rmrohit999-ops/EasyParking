import { Body, Controller, Delete, Get, Param, Patch, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { CheckOwnership } from '../../common/guards/resource-ownership.guard';
import { VehiclesService } from './vehicles.service';
import { CreateVehicleDto, UpdateVehicleDto } from './dto/vehicle.dto';

@ApiTags('vehicles')
@Roles('DRIVER')
@Controller({ path: 'vehicles', version: '1' })
export class VehiclesController {
  constructor(private readonly vehiclesService: VehiclesService) {}

  @Get()
  @ApiOperation({ summary: "List the current driver's active vehicles" })
  list(@CurrentUser() user: AuthenticatedUser) {
    return this.vehiclesService.list(user.id);
  }

  @Post()
  @ApiOperation({ summary: 'Add a vehicle' })
  create(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateVehicleDto) {
    return this.vehiclesService.create(user.id, dto);
  }

  @Patch(':vehicleId')
  @CheckOwnership('VehicleOwnershipResolver', 'vehicleId')
  @ApiOperation({ summary: 'Edit a vehicle (not its category — see admin category-review flow)' })
  update(@CurrentUser() user: AuthenticatedUser, @Param('vehicleId') vehicleId: string, @Body() dto: UpdateVehicleDto) {
    return this.vehiclesService.update(user.id, vehicleId, dto);
  }

  @Delete(':vehicleId')
  @CheckOwnership('VehicleOwnershipResolver', 'vehicleId')
  @ApiOperation({ summary: 'Remove a vehicle' })
  remove(@CurrentUser() user: AuthenticatedUser, @Param('vehicleId') vehicleId: string) {
    return this.vehiclesService.remove(user.id, vehicleId);
  }

  @Post(':vehicleId/default')
  @CheckOwnership('VehicleOwnershipResolver', 'vehicleId')
  @ApiOperation({ summary: 'Set a vehicle as the default for booking' })
  setDefault(@CurrentUser() user: AuthenticatedUser, @Param('vehicleId') vehicleId: string) {
    return this.vehiclesService.setDefault(user.id, vehicleId);
  }
}
