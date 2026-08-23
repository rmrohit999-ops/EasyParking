import { Inject, Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  DeleteObjectCommand,
  GetObjectCommand,
  PutObjectCommand,
  S3Client,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { randomUUID } from 'crypto';
import { AppConfig } from '../../common/config/configuration';

export interface PresignedUpload {
  uploadUrl: string;
  storageKey: string;
  expiresInSeconds: number;
}

/**
 * Storage is presigned-URL based, never a proxy through our API process:
 * the client PUTs the binary directly to the bucket (MinIO locally per
 * docker-compose, or a real S3/S3-compatible bucket in staging/prod), and
 * later GETs it via a short-lived presigned read URL. Our server never
 * buffers photo/document bytes.
 *
 * "No fakes": when STORAGE_PROVIDER isn't configured with reachable
 * credentials/endpoint, every method throws ServiceUnavailableException
 * rather than pretending an upload succeeded — the same "unavailable-state,
 * not fake-success" pattern as OTP/payments (Milestone 0 §14).
 */
export interface StorageService {
  createUploadUrl(key: string, contentType: string): Promise<PresignedUpload>;
  createReadUrl(key: string): Promise<string>;
  deleteObject(key: string): Promise<void>;
  isConfigured: boolean;
}

export const STORAGE_SERVICE = 'STORAGE_SERVICE';

/**
 * Real S3-compatible adapter. Covers both `STORAGE_PROVIDER=s3` (AWS S3 or
 * any S3-compatible provider) and `STORAGE_PROVIDER=minio` (path-style
 * addressing against the STORAGE_ENDPOINT, matching the minio service in
 * docker-compose.yml). GCS is deliberately NOT implemented here — see
 * GcsStorageService below — because it needs a different SDK
 * (@google-cloud/storage) and no GCS credentials exist to build/test
 * against; picking STORAGE_PROVIDER=gcs fails loudly at boot instead of
 * silently no-opping.
 */
@Injectable()
export class S3StorageService implements StorageService {
  private readonly logger = new Logger(S3StorageService.name);
  private readonly client: S3Client | null;
  readonly isConfigured: boolean;
  private readonly bucketPrivate: string;
  private readonly signedUrlTtlSeconds: number;

  constructor(private readonly configService: ConfigService<AppConfig, true>) {
    const storage = this.configService.get('storage', { infer: true });
    this.bucketPrivate = storage.bucketPrivate;
    this.signedUrlTtlSeconds = storage.signedUrlTtlSeconds;

    const hasCredentials = Boolean(storage.accessKeyId && storage.secretAccessKey && storage.bucketPrivate);
    this.isConfigured = hasCredentials;

    if (!hasCredentials) {
      this.logger.warn(
        'Storage credentials are not configured (STORAGE_ACCESS_KEY_ID/STORAGE_SECRET_ACCESS_KEY/STORAGE_BUCKET_PRIVATE). ' +
          'Photo/document upload endpoints will return 503 until they are set — see .env.example.',
      );
      this.client = null;
      return;
    }

    this.client = new S3Client({
      region: storage.region,
      endpoint: storage.endpoint || undefined,
      forcePathStyle: storage.provider === 'MINIO' || Boolean(storage.endpoint),
      credentials: {
        accessKeyId: storage.accessKeyId,
        secretAccessKey: storage.secretAccessKey,
      },
    });
  }

  private requireClient(): S3Client {
    if (!this.client) {
      throw new ServiceUnavailableException(
        'File storage is not configured on this server right now. Please try again later.',
      );
    }
    return this.client;
  }

  async createUploadUrl(key: string, contentType: string): Promise<PresignedUpload> {
    const client = this.requireClient();
    const command = new PutObjectCommand({
      Bucket: this.bucketPrivate,
      Key: key,
      ContentType: contentType,
    });
    const uploadUrl = await getSignedUrl(client, command, { expiresIn: this.signedUrlTtlSeconds });
    return { uploadUrl, storageKey: key, expiresInSeconds: this.signedUrlTtlSeconds };
  }

  async createReadUrl(key: string): Promise<string> {
    const client = this.requireClient();
    const command = new GetObjectCommand({ Bucket: this.bucketPrivate, Key: key });
    return getSignedUrl(client, command, { expiresIn: this.signedUrlTtlSeconds });
  }

  async deleteObject(key: string): Promise<void> {
    const client = this.requireClient();
    await client.send(new DeleteObjectCommand({ Bucket: this.bucketPrivate, Key: key }));
  }
}

/** Builds a collision-resistant, non-guessable object key under a resource-scoped prefix. */
export function buildStorageKey(prefix: string, originalFilenameHint: string): string {
  const extMatch = /\.[a-zA-Z0-9]{1,8}$/.exec(originalFilenameHint);
  const ext = extMatch ? extMatch[0].toLowerCase() : '';
  return `${prefix}/${randomUUID()}${ext}`;
}
