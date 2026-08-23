import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { LoggerModule } from 'nestjs-pino';
import { ConfigService } from '@nestjs/config';
import { AppConfigModule } from './common/config/config.module';
import { PrismaModule } from './common/prisma/prisma.module';
import { CorrelationIdMiddleware } from './common/logging/correlation-id.middleware';
import { AppConfig } from './common/config/configuration';
import { GuardsModule } from './common/guards/guards.module';

import { HealthModule } from './modules/health/health.module';
import { AuthModule } from './modules/auth/auth.module';
import { UsersModule } from './modules/users/users.module';
import { VehiclesModule } from './modules/vehicles/vehicles.module';
import { ParkingModule } from './modules/parking/parking.module';
import { DiscoveryModule } from './modules/discovery/discovery.module';
import { AvailabilityModule } from './modules/availability/availability.module';
import { BookingModule } from './modules/booking/booking.module';
import { PaymentsModule } from './modules/payments/payments.module';
import { LedgerModule } from './modules/ledger/ledger.module';
import { RefundsModule } from './modules/refunds/refunds.module';
import { SettlementsModule } from './modules/settlements/settlements.module';
import { QrModule } from './modules/qr/qr.module';
import { NotificationsModule } from './modules/notifications/notifications.module';
import { FraudModule } from './modules/fraud/fraud.module';
import { ReviewsModule } from './modules/reviews/reviews.module';
import { SupportModule } from './modules/support/support.module';
import { DisputesModule } from './modules/disputes/disputes.module';
import { AdminModule } from './modules/admin/admin.module';
import { ReportsModule } from './modules/reports/reports.module';
import { AuditModule } from './modules/audit/audit.module';
import { ConfigModule as BusinessConfigModule } from './modules/config/config.module';
import { StorageModule } from './modules/storage/storage.module';

@Module({
  imports: [
    AppConfigModule,
    LoggerModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (configService: ConfigService<AppConfig, true>) => ({
        pinoHttp: {
          level: configService.get('logLevel', { infer: true }),
          autoLogging: false, // LoggingInterceptor owns the per-request completion log
          redact: {
            paths: [
              'req.headers.authorization',
              'req.headers.cookie',
              'req.body.password',
              'req.body.otp',
              'req.body.cardNumber',
              'req.body.cvv',
            ],
            censor: '[REDACTED]',
          },
          transport:
            configService.get('env', { infer: true }) === 'development'
              ? { target: 'pino-pretty', options: { colorize: true, singleLine: true } }
              : undefined,
        },
      }),
    }),
    PrismaModule,
    HealthModule,
    GuardsModule,
    // Domain modules — most are empty scaffolds in Milestone 1; see each
    // module's doc comment for which milestone fills it in.
    AuthModule,
    UsersModule,
    VehiclesModule,
    ParkingModule,
    DiscoveryModule,
    AvailabilityModule,
    BookingModule,
    PaymentsModule,
    LedgerModule,
    RefundsModule,
    SettlementsModule,
    QrModule,
    NotificationsModule,
    FraudModule,
    ReviewsModule,
    SupportModule,
    DisputesModule,
    AdminModule,
    ReportsModule,
    AuditModule,
    BusinessConfigModule,
    StorageModule,
  ],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer) {
    consumer.apply(CorrelationIdMiddleware).forRoutes('*');
  }
}
