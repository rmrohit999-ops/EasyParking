import { Global, Module } from '@nestjs/common';
import { ConfigModule as NestConfigModule, ConfigService } from '@nestjs/config';
import configuration from './configuration';
import { envValidationSchema } from './env.validation';

/**
 * Global config module. Imported once in AppModule; every other module
 * injects ConfigService<AppConfig> rather than reading process.env.
 */
@Global()
@Module({
  imports: [
    NestConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
      validationSchema: envValidationSchema,
      validationOptions: { abortEarly: false, allowUnknown: true },
      envFilePath: ['.env.local', '.env'],
    }),
  ],
  exports: [NestConfigModule],
})
export class AppConfigModule {}

export { ConfigService };
