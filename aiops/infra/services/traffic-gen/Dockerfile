FROM alpine:3.19
RUN apk add --no-cache curl
COPY traffic.sh /traffic.sh
RUN chmod +x /traffic.sh
CMD ["/traffic.sh"]
