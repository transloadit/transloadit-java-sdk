package com.transloadit.sdk;

import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import io.tus.java.client.FingerprintNotFoundException;
import io.tus.java.client.ProtocolException;
import io.tus.java.client.ResumingNotEnabledException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;

import java.io.IOException;


/**
 * This class provides a TusUpload as Thread in order to enable parallel Uploads.
 */
class TusUploadThread extends Thread {
    private TusUploader tusUploader;
    private TusUpload tusUpload;
    private TusClient tusClient;
    private TusExecutor tusExecutor;
    private Assembly assembly;

    private long uploadedBytes = 0;

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private Object lock;

    /**
     * Constructs an new Instance of the TusUploadThread.
     * @param tusClient Instance of the current {@link TusClient}.
     * @param tusUpload The {@link TusUpload} to be uploaded.
     * @param assembly The calling Assembly instance
     */
    TusUploadThread(TusClient tusClient, TusUpload tusUpload, Assembly assembly) {
        this.tusClient = tusClient;
        this.tusUpload = tusUpload;
        this.assembly = assembly;
        this.lock = new Object();
        this.tusExecutor = getTusExecutor();

        this.setName("Upload - " + tusUpload.getMetadata().get("filename"));
    }

    /**
     * The method to be started by the Task Executor.
     */
    public void run() {
        try {
            this.tusUploader = tusClient.resumeOrCreateUpload(tusUpload);
        } catch (ProtocolException | IOException e) {
            assembly.threadThrowsRequestException(this.getName(), e);
        }
        this.isRunning = true;
        try {
            tusExecutor.makeAttempts();
        } catch (ProtocolException | IOException e) {
            assembly.threadThrowsRequestException(this.getName(), e);
        } finally {
            System.out.println("Thread :" +  this.getName() + "Has finished");
            assembly.removeThreadFromList(this);
        }
    }

    /**
     * Returns a {@link TusExecutor} instance, which handles upload coordination.
     * This Executor also handles pause States if it's calling thread is paused.
     * @return {@link TusExecutor}
     */
    private TusExecutor getTusExecutor() {
        return new TusExecutor() {
            @Override
            protected void makeAttempt() throws ProtocolException, IOException {
                int uploadedChunk = 0;
                try {
                    while (uploadedChunk > -1) {
                        if (!isRunning) {
                            isRunning = true;
                        }

                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("INTERRUPTED");
                        }

                        if (!isPaused) {
                            isRunning = true;
                            uploadedChunk = tusUploader.uploadChunk();
                            if (uploadedChunk > -1) {
                                assembly.updateUploadProgress(uploadedChunk);
                            }
                        } else {
                            synchronized (lock) {
                                // todo: replace with tusUploader.pause();
                                tusUploader.finish();
                                System.out.println(" is Paused");
                                isRunning = false;
                                lock.wait();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    assembly.threadThrowsLocalOperationException(getName(), e);
                } finally {
                    tusUploader.finish();
                    System.out.println("Finally BLock in TUS Exec");
                }
            }
        };
    }

    /**
     * Sets {@link #isPaused} {@code = true}.
     * This results in pausing the thread after uploading the current chunk.
     */
    public void setPaused() throws LocalOperationException {
        if (!tusClient.resumingEnabled()) {
            throw new LocalOperationException("Resuming has been disabled");
        }
        this.isPaused = true;
    }

    /**
     * Sets {@link #isPaused} {@code = false}.
     * This results in resuming the upload with the next chunk.
     */
    public void setUnPaused() throws LocalOperationException, RequestException {
        try {
            this.tusUploader = this.tusClient.resumeUpload(tusUpload);
        } catch (FingerprintNotFoundException | ResumingNotEnabledException e) {
            throw new LocalOperationException(e);
        } catch (ProtocolException | IOException e) {
            throw new RequestException(e);
        }
        this.isPaused = false;
            synchronized (lock) {
                lock.notify();
            }
            System.out.println(getName() + " is Resuming ");
        }
}
