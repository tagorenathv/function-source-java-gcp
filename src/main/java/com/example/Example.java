package com.example;

import com.example.Example.GCSEvent;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class Example implements BackgroundFunction<GCSEvent> {
    private static final Logger logger = Logger.getLogger(Example.class.getName());

    @Override
    public void accept(GCSEvent event, Context context) throws IOException {
        logger.info("Processing file: " + event.toString());
        // The ID of your GCP project
        String projectId = System.getenv("PROJECT_ID");// "deft-effect-317609";

        // The ID of your GCS bucket
        String bucketName = event.bucket;

        // The ID of your GCS object
        String objectName = event.name;

        // The path to which the file should be downloaded
        String destFilePath = "/tmp/" + event.name;

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();

        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        blob.downloadTo(Paths.get(destFilePath));

        System.out.println(
                "Downloaded object "
                        + objectName
                        + " from bucket name "
                        + bucketName
                        + " to "
                        + destFilePath);

        int trueCount = 0;
        int falseCount = 0;
        File file = new File(destFilePath);
        try (
                FileReader fileReader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(fileReader);) {
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                String[] columns = line.split(",");
                if (Boolean.parseBoolean(columns[2])) {
                    trueCount++;
                } else {
                    falseCount++;
                }
            }
            logger.info("True Status Rows: " + trueCount + " False Status Rows: " + falseCount);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {

            if (trueCount > falseCount) {
                printPayload(projectId, trueCount, falseCount, client, "secretkey1", "secretkey2");
            } else {
                printPayload(projectId, falseCount, trueCount, client, "secretkey2", "secretkey1");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printPayload(String projectId, int trueCount, int falseCount, SecretManagerServiceClient client, String actualSecretId, String fallbackSecretId) {
        SecretVersionName secretVersionName = SecretVersionName.of(projectId, actualSecretId, String.valueOf(trueCount));
        SecretVersion secretVersion = client.getSecretVersion(secretVersionName);
        if (secretVersion.getState().equals(SecretVersion.State.ENABLED)) {
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            System.out.printf("Plaintext: %s\n", response.getPayload().getData().toStringUtf8());
        } else {
            secretVersionName = SecretVersionName.of(projectId, fallbackSecretId, String.valueOf(falseCount));
            secretVersion = client.getSecretVersion(secretVersionName);
            if (secretVersion.getState().equals(SecretVersion.State.ENABLED)) {
                AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
                System.out.printf("Plaintext: %s\n", response.getPayload().getData().toStringUtf8());
            } else {
                System.out.println("No Available Enabled Secret Key versions.");
            }
        }
    }

    public static class GCSEvent {
        String bucket;
        String name;
        String metageneration;

        @Override
        public String toString() {
            return "GCSEvent{" +
                    "bucket='" + bucket + '\'' +
                    ", name='" + name + '\'' +
                    ", metageneration='" + metageneration + '\'' +
                    '}';
        }
    }
}
