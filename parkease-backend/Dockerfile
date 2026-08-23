# syntax=docker/dockerfile:1

FROM node:22-slim AS base
WORKDIR /app
RUN apt-get update -y && apt-get install -y openssl && rm -rf /var/lib/apt/lists/*

FROM base AS deps
COPY package.json package-lock.json* ./
COPY prisma ./prisma
RUN npm install
RUN npx prisma generate

# ---- dev: used by docker-compose for local development (hot reload) --------
FROM deps AS dev
COPY . .
EXPOSE 3000
CMD ["npm", "run", "start:dev"]

# ---- build: compiles TypeScript for production ------------------------------
FROM deps AS build
COPY . .
RUN npm run build

# ---- prod: minimal runtime image --------------------------------------------
FROM base AS prod
ENV NODE_ENV=production
COPY package.json package-lock.json* ./
RUN npm install --omit=dev
COPY --from=build /app/dist ./dist
COPY --from=build /app/prisma ./prisma
COPY --from=build /app/node_modules/.prisma ./node_modules/.prisma
EXPOSE 3000
USER node
CMD ["node", "dist/main.js"]
