# Use Node.js official image
FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
COPY tsconfig.json ./
COPY next.config.mjs ./
COPY next-env.d.ts ./
COPY app ./app
COPY web ./web
COPY shared ./shared
COPY src ./src
COPY vitest.config.ts ./

RUN npm ci
RUN npm run build
RUN npm prune --omit=dev

EXPOSE 3000

ENV NODE_ENV=production

CMD ["npm", "start"]
