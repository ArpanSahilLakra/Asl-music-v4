FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8000 \
    ASL_BEATS_DB_PATH=/data/asl_beats_discovery.db

WORKDIR /app

# Create non-root user and persistent data directory for SQLite WAL database
RUN groupadd -r aslbeats && useradd -r -g aslbeats aslbeats \
    && mkdir -p /data && chown -R aslbeats:aslbeats /data /app

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r /app/requirements.txt

COPY models.py normalizer.py database.py schema.sql main.py /app/
RUN chown -R aslbeats:aslbeats /app

USER aslbeats

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/health', timeout=4)" || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
