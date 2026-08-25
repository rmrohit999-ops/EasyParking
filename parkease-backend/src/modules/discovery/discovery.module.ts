import { Module } from '@nestjs/common';
import { StorageModule } from '../storage/storage.module';
import { DiscoveryController } from './discovery.controller';
import { FavoritesController } from './favorites.controller';
import { DiscoveryService } from './discovery.service';

/**
 * DiscoveryModule — real (Milestone 5: driver discovery). Not scaffolded in
 * Milestone 1 (only modules named in the original spec's folder sketch
 * were pre-created); wired into AppModule here for the first time.
 * StorageModule added so search results can include each listing's
 * primary photo as a presigned read URL (driver home screen's map
 * bottom-sheet preview).
 */
@Module({
  imports: [StorageModule],
  controllers: [DiscoveryController, FavoritesController],
  providers: [DiscoveryService],
  exports: [DiscoveryService],
})
export class DiscoveryModule {}
