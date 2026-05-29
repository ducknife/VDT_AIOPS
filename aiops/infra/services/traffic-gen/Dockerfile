FROM alpine:3.19
RUN apk add --no-cache curl
COPY traffic.sh /traffic.sh
RUN sed -i 's/\r//' /traffic.sh && chmod +x /traffic.sh
CMD ["/traffic.sh"]
