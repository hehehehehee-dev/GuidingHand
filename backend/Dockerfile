FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8080 \
    GUIDINGHAND_STATE_DIR=/var/lib/guidinghand

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt \
    && useradd --create-home --uid 10001 guidinghand \
    && mkdir -p /var/lib/guidinghand \
    && chown -R guidinghand:guidinghand /var/lib/guidinghand /app

COPY core.py speech_input.py gateway.py ./
USER guidinghand
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD python -c "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:'+os.environ.get('PORT','8080')+'/healthz',timeout=3).read()"

CMD ["sh", "-c", "gunicorn --bind 0.0.0.0:${PORT:-8080} --workers 2 --threads 4 --timeout 60 --access-logfile - --error-logfile - 'core:create_application()'"]
