import { Body, Controller, Delete, Get, HttpCode, HttpStatus, Param, Post, Req } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Request } from 'express';
import { Throttle } from '../../common/rate-limit/throttle.decorator';
import { Public } from '../../common/guards/jwt-auth.guard';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { AuthService } from './auth.service';
import {
  ChangePasswordDto,
  ForgotPasswordDto,
  GoogleSignInDto,
  LoginDto,
  OtpRequestDto,
  OtpVerifyDto,
  RefreshDto,
  RegisterDto,
  ResetPasswordDto,
} from './dto/auth.dto';

@ApiTags('auth')
@Controller({ path: 'auth', version: '1' })
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Public()
  @Post('register')
  @ApiOperation({ summary: 'Register with email+password or phone (verify via OTP separately)' })
  register(@Body() dto: RegisterDto, @Req() req: Request) {
    return this.authService.register(dto, req);
  }

  @Public()
  @Throttle({ limit: 5, windowSeconds: 60 })
  @Post('login')
  @ApiOperation({ summary: 'Email + password login' })
  login(@Body() dto: LoginDto, @Req() req: Request) {
    return this.authService.login(dto, req);
  }

  @Public()
  @Throttle({ limit: 5, windowSeconds: 60 })
  @Post('otp/request')
  @ApiOperation({ summary: 'Request an OTP for phone login/registration/reset' })
  requestOtp(@Body() dto: OtpRequestDto) {
    return this.authService.requestOtp(dto);
  }

  @Public()
  @Throttle({ limit: 10, windowSeconds: 60 })
  @Post('otp/verify')
  @ApiOperation({ summary: 'Verify OTP and receive a session (creates the account on first REGISTER/LOGIN verify)' })
  verifyOtp(@Body() dto: OtpVerifyDto, @Req() req: Request) {
    return this.authService.verifyOtpAndLogin(dto, req);
  }

  @Public()
  @Post('google')
  @ApiOperation({ summary: 'Sign in (or sign up) with a verified Google ID token' })
  googleSignIn(@Body() dto: GoogleSignInDto, @Req() req: Request) {
    return this.authService.googleSignIn(dto, req);
  }

  @Public()
  @Post('refresh')
  @ApiOperation({ summary: 'Rotate a refresh token for a new access/refresh pair' })
  refresh(@Body() dto: RefreshDto) {
    return this.authService.refresh(dto.refreshToken);
  }

  @Post('logout')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Revoke the current session' })
  async logout(@CurrentUser() user: AuthenticatedUser & { sessionId: string }) {
    await this.authService.logout(user.sessionId);
  }

  @Public()
  @Throttle({ limit: 5, windowSeconds: 300 })
  @Post('forgot-password')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Request a password reset email (always returns 204, does not reveal account existence)' })
  async forgotPassword(@Body() dto: ForgotPasswordDto) {
    await this.authService.forgotPassword(dto);
  }

  @Public()
  @Post('reset-password')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Complete a password reset using the emailed token' })
  async resetPassword(@Body() dto: ResetPasswordDto) {
    await this.authService.resetPassword(dto);
  }

  @Post('change-password')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Change password for the currently authenticated user' })
  async changePassword(@CurrentUser() user: AuthenticatedUser, @Body() dto: ChangePasswordDto) {
    await this.authService.changePassword(user.id, dto);
  }

  @Get('sessions')
  @ApiOperation({ summary: "List the current user's active sessions/devices" })
  listSessions(@CurrentUser() user: AuthenticatedUser) {
    return this.authService.listSessions(user.id);
  }

  @Delete('sessions/:id')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Revoke a specific session (e.g. remote sign-out of another device)' })
  async revokeSession(@CurrentUser() user: AuthenticatedUser, @Param('id') sessionId: string) {
    await this.authService.revokeSession(user.id, sessionId);
  }

  @Delete('account')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Delete (anonymize) the current account; legally-retained records are preserved per policy' })
  async deleteAccount(@CurrentUser() user: AuthenticatedUser) {
    await this.authService.deleteAccount(user.id);
  }
}
