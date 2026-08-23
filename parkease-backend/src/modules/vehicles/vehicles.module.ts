import { Module } from '@nestjs/common';
import { VehiclesController } from './vehicles.controller';
import { VehiclesService, VehicleOwnershipResolver } from './vehicles.service';

@Module({
  controllers: [VehiclesController],
  providers: [
    VehiclesService,
    // Registered under the string token used by @CheckOwnership(...) so
    // ResourceOwnershipGuard can resolve it via ModuleRef without every
    // consuming module needing a compile-time dependency on this one.
    { provide: 'VehicleOwnershipResolver', useClass: VehicleOwnershipResolver },
  ],
  exports: [VehiclesService],
})
export class VehiclesModule {}
