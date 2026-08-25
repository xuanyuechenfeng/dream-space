FROM node:22-alpine AS dream-build

WORKDIR /src
COPY dream_web/package*.json ./
RUN npm ci
COPY dream_web/ ./
RUN npm run build

FROM node:22-alpine AS manage-build

WORKDIR /src
COPY manage_web/package*.json ./
RUN npm ci
COPY manage_web/ ./
RUN npm run build

FROM nginx:1.27-alpine

COPY --from=dream-build /src/dist /usr/share/nginx/html/dream_web
COPY --from=manage-build /src/dist /usr/share/nginx/html/manage_web
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
