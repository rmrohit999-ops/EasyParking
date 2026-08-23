import { Module } from '@nestjs/common';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { PasswordService } from './services/password.service';
import { TokenService } from './services/token.service';
import { OtpService } from './services/otp.service';
import { GoogleAuthService } from './services/google-auth.service';

@Module({
  controllers: [AuthController],
  providers: [AuthService, PasswordService, TokenService, OtpService, GoogleAuthService],
  // Exported so GuardsModule (JwtAuthGuard needs TokenService) and other
  // domain modules (e.g. password re-verification before a sensitive
  // action) can reuse these without redefining them.
  exports: [AuthService, PasswordService, TokenService, OtpService, GoogleAuthService],
})
export class AuthModule {}
