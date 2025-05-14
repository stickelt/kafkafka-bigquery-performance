# Deploying Kafka-BigQuery Performance App to GKE

This guide outlines the steps to deploy the Kafka-BigQuery Performance application to Google Kubernetes Engine (GKE) using GitHub Actions.

## Prerequisites

- Google Cloud Platform (GCP) account
- GitHub repository with the application code
- GitHub Actions enabled on your repository
- `gcloud` CLI installed locally (only for initial setup)

## Step 1: Set Up GCP Project and GKE Cluster

1. Create a GCP project or use an existing one
2. Enable the following APIs in your GCP project:
   - Google Kubernetes Engine API
   - Container Registry API
   - Artifact Registry API
   - BigQuery API

3. Create a GKE cluster:
   ```bash
   gcloud container clusters create kafka-bq-cluster \
     --zone us-central1-a \
     --num-nodes 3 \
     --machine-type e2-standard-2
   ```

## Step 2: Create Service Account for GitHub Actions

1. Create a service account for GitHub Actions:
   ```bash
   gcloud iam service-accounts create github-actions-sa
   ```

2. Assign necessary roles to the service account:
   ```bash
   gcloud projects add-iam-policy-binding [YOUR_PROJECT_ID] \
     --member="serviceAccount:github-actions-sa@[YOUR_PROJECT_ID].iam.gserviceaccount.com" \
     --role="roles/container.developer"
   
   gcloud projects add-iam-policy-binding [YOUR_PROJECT_ID] \
     --member="serviceAccount:github-actions-sa@[YOUR_PROJECT_ID].iam.gserviceaccount.com" \
     --role="roles/storage.admin"

   gcloud projects add-iam-policy-binding [YOUR_PROJECT_ID] \
     --member="serviceAccount:github-actions-sa@[YOUR_PROJECT_ID].iam.gserviceaccount.com" \
     --role="roles/bigquery.admin"
   ```

3. Create a JSON key for the service account:
   ```bash
   gcloud iam service-accounts keys create key.json \
     --iam-account github-actions-sa@[YOUR_PROJECT_ID].iam.gserviceaccount.com
   ```

4. Add the key as a GitHub secret:
   - Go to your GitHub repository
   - Navigate to Settings > Secrets > Actions
   - Add a new secret named `GCP_SA_KEY` with the content of the `key.json` file
   - Add another secret named `GCP_PROJECT_ID` with your GCP project ID
   - Add a secret named `GKE_CLUSTER_NAME` with the name of your GKE cluster (e.g., "kafka-bq-cluster")
   - Add a secret named `GKE_ZONE` with your GKE zone (e.g., "us-central1-a")

## Step 3: Add Kubernetes Configuration Files

1. Create a `k8s` directory in your project root
2. Add Kubernetes YAML files for deployment, service, and ConfigMap
   - See the files in the `k8s` directory

## Step 4: Add GitHub Actions Workflow

1. Create a `.github/workflows` directory in your project root
2. Add the GitHub Actions workflow YAML file
   - See the `.github/workflows/gke-deploy.yml` file

## Step 5: Push Changes to GitHub

1. Commit all the changes to your repository
2. Push the changes to GitHub
3. The GitHub Actions workflow will automatically build and deploy your application to GKE

## Verifying the Deployment

1. Check the GitHub Actions tab in your repository to see the workflow status
2. Connect to your GKE cluster:
   ```bash
   gcloud container clusters get-credentials kafka-bq-cluster --zone us-central1-a --project [YOUR_PROJECT_ID]
   ```
3. Verify the deployment status:
   ```bash
   kubectl get pods
   kubectl get services
   ```
4. Access the application using the external IP:
   ```bash
   kubectl get services kafka-bq-service
   ```

## Notes on BigQuery Credentials

The application uses a service account to authenticate with BigQuery. The credentials are stored in a Kubernetes Secret and mounted to the pod as a file.

1. Create a service account for the application with BigQuery permissions
2. Download the JSON key
3. Create a Kubernetes Secret from the JSON key:
   ```bash
   kubectl create secret generic bigquery-credentials --from-file=credentials.json=path/to/your/credentials.json
   ```
4. Update the deployment YAML to mount the secret as a file

## Troubleshooting

1. Check the pod logs:
   ```bash
   kubectl logs -f [POD_NAME]
   ```
2. Ensure all required environment variables are set in the ConfigMap
3. Verify that the service account has the necessary permissions
