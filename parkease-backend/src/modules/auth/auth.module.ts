import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../common/config/configuration';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { PasswordService } from './services/password.service';
import { TokenService } from './services/token.service';
import { OtpService } from './services/otp.service';
import { GoogleAuthService } from './services/google-auth.service';
import { AccountLockoutService } from './services/account-lockout.service';
import { SMS_PROVIDER_SERVICE } from './services/provider/sms-provider.interface';
import { TwilioSmsProviderService } from './services/provider/twilio-sms-provider.service';
import { NullSmsProviderService } from './services/provider/null-sms-provider.service';

@Module({
  controllers: [AuthController],
  providers: [
    AuthService,
    PasswordService,
    TokenService,
    OtpService,
    GoogleAuthService,
    AccountLockoutService,
    TwilioSmsProviderService,
    NullSmsProviderService,
    {
      // Selects the concrete SmsProvider once at bootstrap based on
      // SMS_PROVIDER, mirroring PaymentsModule/SettlementsModule's
      // provider-selection factory exactly.
      provide: SMS_PROVIDER_SERVICE,
      inject: [ConfigService, TwilioSmsProviderService, NullSmsProviderService],
      useFactory: (
        configService: ConfigService<AppConfig, true>,
        twilio: TwilioSmsProviderService,
        nullProvider: NullSmsProviderService,
      ) => (configService.get('otp', { infer: true }).smsProvider === 'twilio' ? twilio : nullProvider),
    },
  ],
  // Exported so GuardsModule (JwtAuthGuard needs TokenService) and other
  // domain modules (e.g. password re-verification before a sensitive
  // action) can reuse these without redefining them.
  exports: [AuthService, PasswordService, TokenService, OtpService, GoogleAuthService],
})
export class AuthModule {}
