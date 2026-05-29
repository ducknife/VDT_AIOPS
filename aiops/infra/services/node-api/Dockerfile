# Stage duy nhất — image nhỏ gọn với Node.js 20 LTS trên Alpine Linux
FROM node:20-alpine

# Thư mục làm việc bên trong container
WORKDIR /app

# Copy package.json trước (tách layer để tận dụng Docker layer cache)
# Nếu chỉ thay đổi src/ mà không đổi dependencies, Docker sẽ dùng lại layer npm install
COPY package*.json ./
RUN npm install --omit=dev

# Copy source code
COPY src/ ./src/

# Khai báo port mà container lắng nghe (chỉ là metadata, không tự expose)
EXPOSE 3000

# Lệnh khởi động container
CMD ["node", "src/index.js"]
