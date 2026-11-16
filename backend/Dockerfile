FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev
COPY src ./src
RUN mkdir -p /var/data/product-images
ENV NODE_ENV=production
ENV IMAGE_STORAGE_DIR=/var/data/product-images
EXPOSE 3000
CMD ["npm", "start"]
