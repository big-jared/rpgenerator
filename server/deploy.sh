#!/bin/bash
# Deploy RPGenerator server to Google Cloud Run
#
# Prerequisites:
# 1. Install gcloud CLI: https://cloud.google.com/sdk/docs/install
# 2. gcloud auth login
# 3. gcloud config set project YOUR_PROJECT_ID
# 4. Store your API key in Secret Manager:
#    echo -n "YOUR_GEMINI_API_KEY" | gcloud secrets create gemini-api-key --data-file=-
#
# Usage: ./server/deploy.sh

set -e

PROJECT_ID=$(gcloud config get-value project)
REGION="us-central1"
SERVICE_NAME="rpgenerator"
IMAGE="gcr.io/$PROJECT_ID/$SERVICE_NAME"

echo "=== Deploying RPGenerator to Cloud Run ==="
echo "Project: $PROJECT_ID"
echo "Region:  $REGION"
echo "Service: $SERVICE_NAME"
echo ""

# Build and push container image (from project root for multi-module access)
echo "Building container image..."
cd "$(dirname "$0")/.."
gcloud builds submit --tag "$IMAGE" .

# Ensure GCS bucket exists for persistent game data
BUCKET_NAME="${PROJECT_ID}-rpgenerator-data"
if ! gsutil ls "gs://$BUCKET_NAME" &>/dev/null; then
  echo "Creating GCS bucket gs://$BUCKET_NAME..."
  gsutil mb -l "$REGION" "gs://$BUCKET_NAME"
fi

# Deploy to Cloud Run with Secret Manager + GCS FUSE volume
echo "Deploying to Cloud Run..."
gcloud run deploy "$SERVICE_NAME" \
  --image "$IMAGE" \
  --region "$REGION" \
  --platform managed \
  --allow-unauthenticated \
  --port 8080 \
  --memory 1Gi \
  --cpu 1 \
  --min-instances 1 \
  --max-instances 10 \
  --timeout 300 \
  --set-secrets "GOOGLE_API_KEY=gemini-api-key:latest,GOOGLE_OAUTH_CLIENT_ID=google-oauth-client-id:latest,WORKER_SHARED_SECRET=worker-shared-secret:latest" \
  --set-env-vars "DATA_DIR=/data" \
  --add-volume name=game-data,type=cloud-storage,bucket="$BUCKET_NAME" \
  --add-volume-mount volume=game-data,mount-path=/data \
  --execution-environment gen2 \
  --session-affinity

# Get the service URL
URL=$(gcloud run services describe "$SERVICE_NAME" --region "$REGION" --format 'value(status.url)')
echo ""
echo "=== Deployment Complete ==="
echo "Service URL: $URL"
echo "Health check: $URL/health"
echo "WebSocket: ${URL/https/wss}/ws/game/{sessionId}"
